package com.jeffmony.downloader.listener;

import com.jeffmony.downloader.model.VideoTaskItem;

public interface IDownloadListener {

    //下载等待
    void onDownloadPending(VideoTaskItem item);

    void onDownloadStart(VideoTaskItem item);

    void onDownloadProgress(VideoTaskItem item);

    void onDownloadSpeed(VideoTaskItem item);

    void onDownloadPause(VideoTaskItem item);

    void onDownloadError(VideoTaskItem item);

    void onDownloadSuccess(VideoTaskItem item);
}
