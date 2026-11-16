package sp.phone.ui.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.ReadProgress;
import sp.phone.common.ReadProgressManager;
import com.client.androidnga.core.data.model.ThreadPageInfo;
import sp.phone.param.TopicTitleHelper;
import sp.phone.rxjava.RxUtils;
import sp.phone.theme.ThemeManager;

public class TopicListAdapter extends BaseAppendableAdapter<ThreadPageInfo, TopicListAdapter.TopicViewHolder> {

    public TopicListAdapter(Context context) {
        super(context);
    }

    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TopicViewHolder viewHolder = new TopicViewHolder(LayoutInflater.from(mContext).inflate(R.layout.list_topic, parent, false));
        viewHolder.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PhoneConfiguration.getInstance().getTopicTitleSize());
        RxUtils.clicks(viewHolder.itemView, mOnClickListener);
        viewHolder.itemView.setOnLongClickListener(mOnLongClickListener);
        return viewHolder;
    }

    private int mProgressVersion = ReadProgressManager.getVersion();

    private final ViewTreeObserver.OnWindowFocusChangeListener mFocusListener = hasFocus -> {
        // 从帖子页返回列表时刷新阅读进度
        if (hasFocus && mProgressVersion != ReadProgressManager.getVersion()) {
            mProgressVersion = ReadProgressManager.getVersion();
            notifyDataSetChanged();
        }
    };

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.getViewTreeObserver().addOnWindowFocusChangeListener(mFocusListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.getViewTreeObserver().removeOnWindowFocusChangeListener(mFocusListener);
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public void setData(List<ThreadPageInfo> dataList) {
        if (dataList == null) {
            super.setData(null);
        } else {
            super.appendData(dataList);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {

        ThreadPageInfo info = getItem(position);
        info.setPosition(position);
        holder.itemView.setTag(info);

        handleJsonList(holder, info);
        if (!PhoneConfiguration.getInstance().useSolidColorBackground()) {
            holder.itemView.setBackgroundResource(ThemeManager.getInstance().getBackgroundColor(position));
        }
    }

    private void handleJsonList(TopicViewHolder holder, ThreadPageInfo entry) {

        if (entry == null) {
            return;
        }
        holder.author.setText(entry.getAuthor());
        holder.lastReply.setText(entry.getLastPoster());
        holder.num.setText(String.valueOf(entry.getReplies()));
        holder.title.setText(TopicTitleHelper.handleTitleFormat(entry));
        bindReadProgress(holder, entry);
    }

    private void bindReadProgress(TopicViewHolder holder, ThreadPageInfo entry) {
        ReadProgress progress = ReadProgressManager.get(entry.getTid());
        if (progress == null) {
            holder.readProgress.setVisibility(View.GONE);
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (progress.getReadFloor() > 0) {
            builder.append("读到 ").append(progress.getReadFloor()).append(" 楼");
        } else {
            builder.append("看过主楼");
        }
        int newReplies = progress.newReplies(entry.getReplies());
        if (newReplies > 0) {
            builder.append(" · 新增 ").append(newReplies).append(" 楼");
        }
        holder.readProgress.setText(builder);
        holder.readProgress.setVisibility(View.VISIBLE);
    }

    public class TopicViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.num)
        public TextView num;

        @BindView(R.id.title)
        public TextView title;

        @BindView(R.id.author)
        public TextView author;

        @BindView(R.id.last_reply)
        public TextView lastReply;

        @BindView(R.id.read_progress)
        public TextView readProgress;

        public TopicViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
