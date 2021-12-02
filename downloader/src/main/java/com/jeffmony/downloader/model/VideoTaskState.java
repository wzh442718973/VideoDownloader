package com.jeffmony.downloader.model;

public class VideoTaskState {
    public static final int DEFAULT = 0;//默认状态,排队下载
    public static final int DOWNLOADING = 3;//下载中
    public static final int PROXYREADY = 4; //视频可以边下边播
    public static final int SUCCESS = 5;//完成
    public static final int ERROR = 6;//下载出错
    public static final int PAUSE = 7;//下载暂停
    public static final int ENOSPC = 8;//空间不足

    public static final int DOWN_SUCCESS = 9;//下载完成
    public static final int MERGE_ERROR = 10;//合并出错
}
