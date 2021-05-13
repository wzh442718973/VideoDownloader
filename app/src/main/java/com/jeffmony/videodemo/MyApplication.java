package com.jeffmony.videodemo;

import android.app.Application;

import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.VideoDownloadConfig;
import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.IDownloadInitCallback;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.VideoStorageUtils;

import java.io.File;
import java.util.List;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        File file = VideoStorageUtils.getVideoCacheDir(this);
        if (!file.exists()) {
            file.mkdir();
        }
        VideoDownloadConfig config = new VideoDownloadManager.Build(this)
                .setCacheRoot(file.getAbsolutePath())
                .setTimeOut(DownloadConstants.READ_TIMEOUT, DownloadConstants.CONN_TIMEOUT)
                .setConcurrentCount(DownloadConstants.CONCURRENT)
                .setIgnoreCertErrors(false)
                .setShouldM3U8Merged(true)
                .buildConfig();
        VideoDownloadManager.getInstance().initConfig(config, new IDownloadInitCallback(){

            @Override
            public void onDownloadInfos(boolean success, String msg, List<VideoTaskItem> items) {

            }
        });
    }
}
