package com.jeffmony.videodemo.download;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.videodemo.play.PlayerActivity;
import com.jeffmony.videodemo.R;

import java.io.File;
import java.util.List;

public class VideoDownloadListAdapter extends BaseAdapter {

    private static final String TAG = "VideoListAdapter";

    private Context mContext;
    private List<VideoTaskItem> mItems;

    public VideoDownloadListAdapter(Context context, int resource, List<VideoTaskItem> items) {
        super();
        mItems = items;
        mContext = context;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public VideoTaskItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.download_item, null);
        VideoTaskItem item = getItem(position);
        TextView urlTextView = (TextView) view.findViewById(R.id.url_text);
        urlTextView.setText(item.getUrl());
        TextView stateTextView = (TextView) view.findViewById(R.id.status_txt);
        TextView playBtn = (TextView) view.findViewById(R.id.download_play_btn);
        playBtn.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, PlayerActivity.class);
            String filePath = item.getFilePath();
            File file = new File(filePath);
            if (file.exists()) {
                intent.putExtra("videoUrl", item.getFilePath());
                mContext.startActivity(intent);
            } else {
                LogUtils.w(TAG, "File = " + filePath + " is gone");
            }

        });
        setStateText(stateTextView, playBtn, item);
        TextView infoTextView = (TextView) view.findViewById(R.id.download_txt);
        setDownloadInfoText(infoTextView, item);
        return view;
    }

    private void setStateText(TextView stateView, TextView playBtn, VideoTaskItem item) {
        switch (item.getTaskState()) {
            case VideoTaskState.PENDING:
                playBtn.setVisibility(View.INVISIBLE);
                stateView.setText("等待中");
                break;
            case VideoTaskState.PREPARE:
                stateView.setText("下载准备中...");
                break;
            case VideoTaskState.START:
            case VideoTaskState.DOWNLOADING:
                stateView.setText("下载中...");
                break;
            case VideoTaskState.PAUSE:
                playBtn.setVisibility(View.INVISIBLE);
                stateView.setText("下载暂停, 已下载=" + item.getDownloadSizeString());
                break;
            case VideoTaskState.SUCCESS:
                playBtn.setVisibility(View.VISIBLE);
                stateView.setText("下载完成, 总大小=" + item.getDownloadSizeString());
                break;
            case VideoTaskState.ERROR:
                playBtn.setVisibility(View.INVISIBLE);
                stateView.setText("下载错误");
                break;
            default:
                playBtn.setVisibility(View.INVISIBLE);
                stateView.setText("未下载");
                break;

        }
    }

    private void setDownloadInfoText(TextView infoView, VideoTaskItem item) {
        switch (item.getTaskState()) {
            case VideoTaskState.DOWNLOADING:
                infoView.setText("进度:" + item.getPercentString() + ", 速度:" + item.getSpeedString() + ", 已下载:" + item.getDownloadSizeString());
                break;
            case VideoTaskState.SUCCESS:
                infoView.setText("进度:" + item.getPercentString());
                break;
            case VideoTaskState.PAUSE:
                infoView.setText("进度:" + item.getPercentString());
                break;
            default:
                break;
        }
    }

    public void notifyChanged(List<VideoTaskItem> items, VideoTaskItem item) {
        for (int index = 0; index < getCount(); index++) {
            if (getItem(index).equals(item)) {
                items.set(index, item);
                notifyDataSetChanged();
            }
        }
    }

}
