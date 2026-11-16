package sp.phone.mvp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * @author yangyihang
 */
public class ArticleShareViewModel extends ViewModel {

    private MutableLiveData<Integer> mReplyCount = new MutableLiveData<>();

    private MutableLiveData<Integer> mRefreshPage = new MutableLiveData<>();

    private MutableLiveData<Integer> mCachePage = new MutableLiveData<>();

    /** 打开帖子时要恢复的阅读位置，页码为 0 表示没有待恢复的位置 */
    public int restorePage;

    public int restoreIndex;

    public int restoreOffset;

    public void clearRestore() {
        restorePage = 0;
        restoreIndex = 0;
        restoreOffset = 0;
    }

    public MutableLiveData<Integer> getReplyCount() {
        return mReplyCount;
    }

    public void setReplyCount(int replyCount) {
        mReplyCount.setValue(replyCount);
    }

    public MutableLiveData<Integer> getRefreshPage() {
        return mRefreshPage;
    }

    public void setRefreshPage(int refreshPage) {
        mRefreshPage.setValue(refreshPage);
    }

    public MutableLiveData<Integer> getCachePage() {
        return mCachePage;
    }

    public void setCachePage(int cachePage) {
        mCachePage.setValue(cachePage);
    }

}
