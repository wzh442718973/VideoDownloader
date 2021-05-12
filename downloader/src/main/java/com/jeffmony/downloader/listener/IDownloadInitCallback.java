package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.model.VideoTaskItem;

import java.util.List;

public interface IDownloadInitCallback {

    void onDownloadInfos(boolean success, String msg, List<VideoTaskItem> items);
}
