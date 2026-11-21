package sp.phone.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.android.arouter.launcher.ARouter;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.BaseActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import io.reactivex.annotations.NonNull;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.ReadProgress;
import sp.phone.common.ReadProgressManager;
import sp.phone.common.User;
import sp.phone.common.UserManagerImpl;
import com.client.androidnga.core.data.model.ThreadInfo;
import com.client.androidnga.core.data.model.ThreadPostInfo;

import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.presenter.ArticleListPresenter;
import sp.phone.mvp.viewmodel.ArticleShareViewModel;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.rxjava.RxEvent;
import sp.phone.task.BookmarkTask;
import sp.phone.ui.adapter.ArticleListAdapter;
import sp.phone.ui.fragment.dialog.BaseDialogFragment;
import sp.phone.ui.fragment.dialog.PostCommentDialogFragment;
import sp.phone.util.ActivityUtils;
import sp.phone.util.FunctionUtils;
import gov.anzong.androidnga.common.util.NLog;
import sp.phone.util.StringUtils;
import sp.phone.view.FloorScrollBar;
import sp.phone.view.RecyclerViewEx;

/*
 * MD 帖子详情每一页
 */
public class ArticleListFragment extends BaseMvpFragment<ArticleListPresenter> implements ArticleListContract.View {

    private static final String TAG = ArticleListFragment.class.getSimpleName();

    @BindView(R.id.list)
    public RecyclerViewEx mListView;

    @BindView(R.id.loading_view)
    public View mLoadingView;

    @BindView(R.id.swipe_refresh)
    public SwipeRefreshLayout mSwipeRefreshLayout;

    private ArticleListAdapter mArticleAdapter;

    protected ArticleListParam mRequestParam;

    private ThreadInfo mThreadInfo;

    /** Fork: ViewPager 里当前显示页是否处于活跃状态（onPause 时 Fragment.isResumed() 已经是 false，不能用它判断） */
    private boolean mActive;

    /** Fork: 当前页是否发生过用户主动滚动或跳楼 */
    private boolean mUserScrolled;

    /** Fork: 刚恢复到上次位置且用户尚未滚动过，退出时保持原位置避免漂移 */
    private boolean mRestoredUntouched;

    /** Fork: 恢复位置后在 WebView 撑开高度期间锁定目标楼层，-1 表示未锁定 */
    private int mPendingRestoreIndex = -1;

    private boolean mRestoreListenerAttached;

    private final Runnable mClearRestoreLockRunnable = this::clearRestoreLock;

    private final ViewTreeObserver.OnGlobalLayoutListener mRestoreLayoutListener = () -> {
        if (mPendingRestoreIndex < 0 || mListView == null || mListView.isInLayout()) {
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) mListView.getLayoutManager();
        if (lm == null) {
            return;
        }
        int count = mArticleAdapter == null ? 0 : mArticleAdapter.getItemCount();
        if (mPendingRestoreIndex >= count) {
            clearRestoreLock();
            return;
        }
        int desiredTop = mListView.getPaddingTop();
        View target = lm.findViewByPosition(mPendingRestoreIndex);
        boolean needsScroll;
        if (target == null) {
            needsScroll = true;
        } else {
            int diff = target.getTop() - desiredTop;
            needsScroll = (diff > 2 && mListView.canScrollVertically(1))
                    || (diff < -2 && mListView.canScrollVertically(-1));
        }
        if (needsScroll) {
            lm.scrollToPositionWithOffset(mPendingRestoreIndex, 0);
        }
    };

    private OnTopicMenuItemClickListener mMenuItemClickListener = new OnTopicMenuItemClickListener() {

        private ThreadPostInfo mThreadRowInfo;

        @Override
        public void setThreadRowInfo(ThreadPostInfo threadRowInfo) {
            mThreadRowInfo = threadRowInfo;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mPresenter == null) {
                return false;
            }

            ThreadPostInfo row = mThreadRowInfo;
            if (row == null) {
                return false;
            }

            String pidStr = String.valueOf(row.pid);
            String tidStr = String.valueOf(row.tid);
            int tid = row.tid;

            switch (item.getItemId()) {
                case R.id.menu_edit:
                    if (mThreadRowInfo.isComment) {
                        showToast(R.string.cannot_eidt_comment);
                        break;
                    } else {
                        ARouter.getInstance()
                                .build(ARouterConstants.ACTIVITY_POST)
                                .withString(ParamKey.KEY_PID, pidStr)
                                .withString(ParamKey.KEY_TID, tidStr)
                                .withString("title", StringUtils.unEscapeHtml(row.subject))
                                .withString("action", "modify")
                                .withString("prefix", StringUtils.unEscapeHtml(StringUtils.removeBrTag(row.rawContent)))
                                .navigation(getActivity(), ActivityUtils.REQUEST_CODE_LOGIN);
                    }
                    break;
                case R.id.menu_post_comment:
                    mPresenter.postComment(mRequestParam, row);
                    break;
                case R.id.menu_report:
                    FunctionUtils.handleReport(row, mRequestParam.tid, getFragmentManager());
                    break;
                case R.id.menu_signature:
                    if (row.isAnonymous) {
                        ActivityUtils.showToast("这白痴匿名了,神马都看不到");
                    } else {
                        FunctionUtils.Create_Signature_Dialog(row, getActivity(),
                                mListView);
                    }
                    break;
                case R.id.menu_vote:
                    FunctionUtils.createVoteDialog(row, getActivity(), mListView, mToast);
                    break;
                case R.id.menu_ban_this_one:
                    mPresenter.banThisSB(row);
                    break;
                case R.id.menu_show_this_person_only:
                    ARouter.getInstance()
                            .build(ARouterConstants.ACTIVITY_TOPIC_CONTENT)
                            .withString("tab", "1")
                            .withInt(ParamKey.KEY_TID, tid)
                            .withInt(ParamKey.KEY_AUTHOR_ID, row.authorId)
                            .withInt("fromreplyactivity", 1)
                            .navigation();
                    break;
                case R.id.menu_support:
                    mPresenter.postSupportTask(tid, row.pid);
                    break;
                case R.id.menu_oppose:
                    mPresenter.postOpposeTask(tid, row.pid);
                    break;
                case R.id.menu_favorite:
                    BookmarkTask.execute(tidStr, pidStr);
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    private View.OnClickListener mMenuTogglerListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            mMenuItemClickListener.setThreadRowInfo((ThreadPostInfo) view.getTag());
            int menuId;
            if (mRequestParam.pid == 0) {
                menuId = R.menu.article_list_context_menu;
            } else {
                menuId = R.menu.article_list_context_menu_with_tid;
            }
            PopupMenu popupMenu = new PopupMenu(getContext(), view);
            popupMenu.inflate(menuId);
            onPrepareOptionsMenu(popupMenu.getMenu(), (ThreadPostInfo) view.getTag());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(mMenuItemClickListener);
        }

        private void onPrepareOptionsMenu(Menu menu, ThreadPostInfo row) {
            MenuItem item = menu.findItem(R.id.menu_ban_this_one);
            if (item != null) {
                item.setTitle(row.isBlocked ? R.string.cancel_ban_thisone : R.string.ban_thisone);
            }

            if (row.isComment) {
                item = menu.findItem(R.id.menu_post_comment);
                if (item != null) {
                    item.setVisible(false);
                }
                item = menu.findItem(R.id.menu_show_this_person_only);
                if (item != null) {
                    item.setVisible(false);
                }
            }

            item = menu.findItem(R.id.menu_vote);
            if (item != null && StringUtils.isEmpty(row.vote)) {
                item.setVisible(false);
            }

            item = menu.findItem(R.id.menu_edit);
            if (item != null) {
                User user = UserManagerImpl.getInstance().getActiveUser();
                if (user == null || !user.getUserId().equals(String.valueOf(row.authorId))) {
                    item.setVisible(false);
                }
            }
        }

    };

    private View.OnClickListener mSupportListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadPostInfo row = (ThreadPostInfo) view.getTag();
            mPresenter.postSupportTask(row.tid, row.pid);
        }
    };

    private View.OnClickListener mOpposeListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ThreadPostInfo row = (ThreadPostInfo) view.getTag();
            mPresenter.postOpposeTask(row.tid, row.pid);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        NLog.d(TAG, "onCreate");
        mRequestParam = getArguments().getParcelable(ParamKey.KEY_PARAM);
        registerRxBus();

        initData();
        super.onCreate(savedInstanceState);
    }

    private void initData() {
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        viewModel.getRefreshPage().observe(this, page -> {
            if (page == mRequestParam.page) {
                loadPage();
            }
        });

        viewModel.getCachePage().observe(this, page -> {
            if (page == mRequestParam.page) {
                mPresenter.cachePage();
            }
        });
    }

    @Override
    protected void accept(@NonNull RxEvent rxEvent) {
        if (rxEvent.what == RxEvent.EVENT_ARTICLE_GO_FLOOR
                && rxEvent.arg + 1 == mRequestParam.page
                && rxEvent.obj != null) {
            ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
            if (mThreadInfo == null) {
                return;
            }
            int target = (Integer) rxEvent.obj;
            int count = mArticleAdapter == null ? 0 : mArticleAdapter.getItemCount();
            if (count > 0) {
                target = Math.max(0, Math.min(target, count - 1));
            }
            boolean isRestore = viewModel.restorePage == mRequestParam.page;
            if (isRestore) {
                viewModel.clearRestore();
            } else {
                mUserScrolled = true;
            }
            startRestoreLock(target, isRestore);
        }
    }

    @Override
    protected ArticleListPresenter onCreatePresenter() {
        return new ArticleListPresenter(mRequestParam);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ButterKnife.bind(this, view);
        ((BaseActivity) getActivity()).setupToolbar();
        mArticleAdapter = new ArticleListAdapter(getContext(),getActivity().getSupportFragmentManager());
        mArticleAdapter.setSupportListener(mSupportListener);
        mArticleAdapter.setOpposeListener(mOpposeListener);
        mArticleAdapter.setMenuTogglerListener(mMenuTogglerListener);
        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mListView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@androidx.annotation.NonNull RecyclerView rv, @androidx.annotation.NonNull MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    clearRestoreLock();
                }
                return false;
            }
        });
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@androidx.annotation.NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearRestoreLock();
                    mRestoredUntouched = false;
                    mUserScrolled = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && mUserScrolled) {
                    saveReadProgress(true);
                }
            }
        });
        mListView.setItemViewCacheSize(20);
        mListView.setAdapter(mArticleAdapter);
        mListView.setEmptyView(view.findViewById(R.id.empty_view));
        if (PhoneConfiguration.getInstance().useSolidColorBackground()) {
            // Fork: 最后一楼下面不画分隔线（原为 DividerItemDecoration），否则拉到底时压在玻璃页码栏上沿
            mListView.addItemDecoration(new sp.phone.view.InnerDividerDecoration(view.getContext()));
        }

        // Fork: 玻璃标题栏和页码栏盖在列表上，列表上下留出它们的高度
        sp.phone.view.glass.ArticleGlass.applyListInsets(getActivity(), view, mListView);

        // Fork: 右侧楼层滚动条，滑动时显示当前第几楼，可拖动快速跳楼
        FloorScrollBar.attach((ViewGroup) view, mListView, new FloorScrollBar.Source() {
            @Override
            public int floorAt(int position) {
                if (mThreadInfo == null || position < 0 || position >= mThreadInfo.postInfoList.size()) {
                    return -1;
                }
                ThreadPostInfo row = mThreadInfo.postInfoList.get(position);
                return row == null ? -1 : row.lou;
            }

            @Override
            public int lastFloor() {
                return mThreadInfo == null ? -1 : mThreadInfo.totalRows - 1;
            }

            @Override
            public void onDragStart() {
                clearRestoreLock();
                mRestoredUntouched = false;
                mUserScrolled = true;
            }

            @Override
            public void onDragEnd() {
                saveReadProgress(true);
            }
        });

        TextView sayingView = (TextView) mLoadingView.findViewById(R.id.saying);
        sayingView.setText(ActivityUtils.getSaying());

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadPage();
            }
        });
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mActive = true;
    }

    @Override
    public void onPause() {
        if (mActive) {
            saveReadProgress(true);
            mActive = false;
        }
        clearRestoreLock();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        clearRestoreLock();
        super.onDestroyView();
    }

    private void startRestoreLock(int index, boolean untouched) {
        clearRestoreLock();
        mRestoredUntouched = untouched;
        if (mListView == null) {
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) mListView.getLayoutManager();
        if (lm == null) {
            return;
        }
        mPendingRestoreIndex = index;
        lm.scrollToPositionWithOffset(index, 0);
        mListView.getViewTreeObserver().addOnGlobalLayoutListener(mRestoreLayoutListener);
        mRestoreListenerAttached = true;
        mListView.postDelayed(mClearRestoreLockRunnable, 2000);
    }

    private void clearRestoreLock() {
        mPendingRestoreIndex = -1;
        if (mListView != null) {
            mListView.removeCallbacks(mClearRestoreLockRunnable);
            if (mRestoreListenerAttached) {
                ViewTreeObserver vto = mListView.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnGlobalLayoutListener(mRestoreLayoutListener);
                }
                mRestoreListenerAttached = false;
            }
        }
    }

    private boolean isProgressTrackable() {
        return mRequestParam != null
                && mRequestParam.tid != 0
                && mRequestParam.pid == 0
                && mRequestParam.authorId == 0
                && mRequestParam.searchPost == 0;
    }

    /**
     * Fork: 记录当前页的阅读位置，只记录 ViewPager 里当前显示的那一页（由 mActive 标识，
     * 避免 Fragment.onPause() 时 isResumed() 已为 false 导致退出位置从未保存）。
     */
    private void saveReadProgress(boolean measure) {
        if (!mActive || !isProgressTrackable() || mListView == null || mThreadInfo == null
                || mThreadInfo.postInfoList.isEmpty()
                || (mLoadingView != null && mLoadingView.getVisibility() == View.VISIBLE)) {
            return;
        }
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        // 还有待恢复的位置时不记录，免得把要回到的位置覆盖掉
        if (viewModel.restorePage == mRequestParam.page) {
            return;
        }
        if (mRestoredUntouched) {
            // 恢复位置后用户没滚动过就退出：保留原位置，只在有新回复时更新总楼数
            ReadProgress old = ReadProgressManager.get(mRequestParam.tid);
            if (old != null && old.getPage() == mRequestParam.page) {
                if (mThreadInfo.totalRows > old.getRows()) {
                    ReadProgressManager.save(mRequestParam.tid, old.getPage(), old.getIndex(), 0,
                            old.getReadFloor(), mThreadInfo.totalRows, old.getSubject());
                }
                return;
            }
        }
        LinearLayoutManager lm = (LinearLayoutManager) mListView.getLayoutManager();
        if (lm == null) {
            return;
        }
        int size = mThreadInfo.postInfoList.size();
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first < 0 || first >= size) {
            return;
        }
        if (last < first || last >= size) {
            last = first;
        }
        int visibleTop = sp.phone.view.glass.ArticleGlass.visibleTop(getActivity(), mListView);
        int visibleBottom = mListView.getHeight() - mListView.getPaddingBottom();
        int visibleHeight = visibleBottom - visibleTop;
        if (visibleHeight <= 0) {
            return;
        }

        // 锚点只记楼层序号、偏移固定 0：非零偏移会让上方的楼在 WebView 撑开后把目标楼挤出屏幕
        int anchor;
        if (!mListView.canScrollVertically(1) && mListView.canScrollVertically(-1)) {
            // 已经滑到本页底部：记最后一楼，再次打开停在底部，有新回复时正好从最后看过的楼开始
            anchor = size - 1;
        } else {
            // 跳过完全藏在玻璃顶栏后面的楼；顶上那一楼若只剩底部一小截且下一楼在屏幕上半部分，记下一楼
            anchor = first;
            for (int i = first; i <= last; i++) {
                View v = lm.findViewByPosition(i);
                if (v != null && v.getBottom() > visibleTop) {
                    anchor = i;
                    break;
                }
            }
            View anchorView = lm.findViewByPosition(anchor);
            if (anchorView != null && anchorView.getTop() < visibleTop
                    && anchorView.getBottom() - visibleTop < visibleHeight * 0.25f
                    && anchor + 1 <= last) {
                View next = lm.findViewByPosition(anchor + 1);
                if (next != null && next.getTop() - visibleTop < visibleHeight * 0.6f) {
                    anchor = anchor + 1;
                }
            }
        }

        // 读到的楼层：楼头已经进入屏幕的最后一楼（露出不到 1/10 屏的不算）
        int floorIndex = anchor;
        if (measure) {
            for (int i = last; i > anchor; i--) {
                View v = lm.findViewByPosition(i);
                if (v != null && v.getTop() - visibleTop < visibleHeight * 0.9f) {
                    floorIndex = i;
                    break;
                }
            }
        }
        ThreadPostInfo row = mThreadInfo.postInfoList.get(floorIndex);
        int floor = row == null ? 0 : row.lou;
        if (!measure) {
            ReadProgress old = ReadProgressManager.get(mRequestParam.tid);
            if (old != null && old.getPage() == mRequestParam.page) {
                floor = Math.max(floor, old.getReadFloor());
            }
        }
        String subject = mRequestParam.title;
        if (subject == null && mThreadInfo.pageInfo != null) {
            subject = mThreadInfo.pageInfo.getSubject();
        }
        if (viewModel.restorePage != 0) {
            viewModel.clearRestore();
        }
        ReadProgressManager.save(mRequestParam.tid, mRequestParam.page, anchor, 0, floor,
                mThreadInfo.totalRows, subject);
    }

    public void loadPage() {
        mPresenter.loadPage(mRequestParam);
    }

    @Override
    public void setData(ThreadInfo data) {
        ArticleShareViewModel viewModel = getActivityViewModelProvider().get(ArticleShareViewModel.class);
        if (getActivity() != null && data != null) {
            viewModel.setReplyCount(data.totalRows);
        }
        if (data != null && data.pageInfo != null && getActivity() != null && mRequestParam.title == null) {
            getActivity().setTitle(data.pageInfo.getSubject());
        }

        mThreadInfo = data;
        mArticleAdapter.setData(data);
        mArticleAdapter.notifyDataSetChanged();

        if (data != null && viewModel.restorePage != 0 && viewModel.restorePage == mRequestParam.page) {
            int index = Math.min(viewModel.restoreIndex, Math.max(0, mArticleAdapter.getItemCount() - 1));
            viewModel.clearRestore();
            startRestoreLock(index, true);
        }

    }

    @Override
    public void startPostActivity(Intent intent) {
        if (!StringUtils.isEmpty(UserManagerImpl.getInstance().getUserName())) {// 登入了才能发
            intent.setClass(getActivity(), PhoneConfiguration.getInstance().postActivityClass);
        } else {
            ActivityUtils.startLoginActivity(getActivity());
        }
        startActivityForResult(intent, ActivityUtils.REQUEST_CODE_TOPIC_POST);
    }

    @Override
    public void showPostCommentDialog(String prefix, Bundle bundle) {
        BaseDialogFragment df = new PostCommentDialogFragment();
        df.setArguments(bundle);
        df.show(getActivity().getSupportFragmentManager());
    }


    @Override
    public void setRefreshing(boolean refreshing) {
        if (mSwipeRefreshLayout.isShown()) {
            mSwipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    @Override
    public boolean isRefreshing() {
        return mSwipeRefreshLayout.isShown() ? mSwipeRefreshLayout.isRefreshing() : mLoadingView.isShown();
    }

    @Override
    public void hideLoadingView() {
        mLoadingView.setVisibility(View.GONE);
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    interface OnTopicMenuItemClickListener extends PopupMenu.OnMenuItemClickListener {

        void setThreadRowInfo(ThreadPostInfo threadRowInfo);

    }


}
