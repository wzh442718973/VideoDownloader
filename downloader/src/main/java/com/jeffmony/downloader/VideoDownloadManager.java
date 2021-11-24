package com.jeffmony.downloader;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jeffmony.downloader.common.DownloadConstants;
import com.jeffmony.downloader.database.VideoDownloadDatabaseHelper;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInitCallback;
import com.jeffmony.downloader.listener.IDownloadTaskListener;
import com.jeffmony.downloader.listener.IVideoInfoListener;
import com.jeffmony.downloader.listener.IVideoInfoParseListener;
import com.jeffmony.downloader.m3u8.M3U8;
import com.jeffmony.downloader.model.Video;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.process.IM3U8MergeListener;
import com.jeffmony.downloader.process.IM3U8MergeResultListener;
import com.jeffmony.downloader.process.VideoProcessManager;
import com.jeffmony.downloader.task.BaseVideoDownloadTask;
import com.jeffmony.downloader.task.M3U8VideoDownloadTask;
import com.jeffmony.downloader.task.VideoDownloadTask;
import com.jeffmony.downloader.utils.ContextUtils;
import com.jeffmony.downloader.utils.DownloadExceptionUtils;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.downloader.utils.VideoDownloadUtils;
import com.jeffmony.downloader.utils.VideoStorageUtils;
import com.jeffmony.downloader.utils.WorkerThreadHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoDownloadManager {
    private static final String TAG = "VideoDownloadManager";
    private static final boolean DEBUG = true;

    private static volatile VideoDownloadManager sInstance = null;
    private DownloadListener mGlobalDownloadListener = null;
    //wzh add
    private OnRedirectListener mGlobalRedirectListener = null;
    //wzh end
    private VideoDownloadDatabaseHelper mVideoDatabaseHelper = null;
    private VideoDownloadQueue mVideoDownloadQueue;
    private Object mQueueLock = new Object();
    private VideoDownloadConfig mConfig;

    private VideoDownloadHandler mVideoDownloadHandler;
    private IDownloadInitCallback mInitCallbacks;
    private Map<String, VideoDownloadTask> mVideoDownloadTaskMap = new ConcurrentHashMap<>();
    //    private Map<String, VideoTaskItem> mVideoItemTaskMap = new ConcurrentHashMap<>();
    private final AtomicBoolean mInitialize = new AtomicBoolean(false);

    private final void waitInit() {
        synchronized (mInitialize) {
            while (!mInitialize.get()) {
                try {
                    mInitialize.wait(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public VideoTaskItem getTaskItem(String url) {
        waitInit();
        return mVideoDownloadQueue.getTaskItem(url);
    }

    public List<VideoTaskItem> getAllTaskItems() {
        waitInit();
        return mVideoDownloadQueue.getDownloadList();
    }


    public static class Build {
        private String mCacheRoot;
        private int mReadTimeOut = 60 * 1000;              // 60 seconds
        private int mConnTimeOut = 60 * 1000;              // 60 seconds
        private boolean mIgnoreCertErrors = false;
        private int mConcurrentCount = 3;
        private boolean mShouldM3U8Merged = false;

        public Build(@NonNull Context context) {
            ContextUtils.initApplicationContext(context);
        }

        //设置下载目录
        public Build setCacheRoot(String cacheRoot) {
            mCacheRoot = cacheRoot;
            return this;
        }

        //设置超时时间
        public Build setTimeOut(int readTimeOut, int connTimeOut) {
            mReadTimeOut = readTimeOut;
            mConnTimeOut = connTimeOut;
            return this;
        }

        //设置并发下载的个数
        public Build setConcurrentCount(int count) {
            mConcurrentCount = count;
            return this;
        }

        //是否信任证书
        public Build setIgnoreCertErrors(boolean ignoreCertErrors) {
            mIgnoreCertErrors = ignoreCertErrors;
            return this;
        }

        //M3U8下载成功之后是否自动合并
        public Build setShouldM3U8Merged(boolean shouldM3U8Merged) {
            mShouldM3U8Merged = shouldM3U8Merged;
            return this;
        }

        public VideoDownloadConfig buildConfig() {
            return new VideoDownloadConfig(mCacheRoot, mReadTimeOut, mConnTimeOut, mIgnoreCertErrors, mConcurrentCount, mShouldM3U8Merged);
        }
    }

    public void setConcurrentCount(int count) {
        if (mConfig != null) {
            mConfig.setConcurrentCount(count);
        }
    }

    public void setIgnoreAllCertErrors(boolean enable) {
        if (mConfig != null) {
            mConfig.setIgnoreAllCertErrors(enable);
        }
    }

    public void setShouldM3U8Merged(boolean enable) {
        if (mConfig != null) {
            LogUtils.w(TAG, "setShouldM3U8Merged = " + enable);
            mConfig.setShouldM3U8Merged(enable);
        }
    }

    public static VideoDownloadManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoDownloadManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoDownloadManager();
                }
            }
        }
        return sInstance;
    }

    private VideoDownloadManager() {
        mVideoDownloadQueue = new VideoDownloadQueue();
    }

    public void initConfig(@NonNull VideoDownloadConfig config, @NonNull IDownloadInitCallback callback) {
        //如果为null, 会crash
        mConfig = config;
        VideoDownloadUtils.setDownloadConfig(config);
        mVideoDatabaseHelper = new VideoDownloadDatabaseHelper(ContextUtils.getApplicationContext());
        HandlerThread stateThread = new HandlerThread("Video_download_state_thread");
        stateThread.start();
        mVideoDownloadHandler = new VideoDownloadHandler(stateThread.getLooper());
        mInitCallbacks = callback;
        fetchDownloadItems();
    }

    public VideoDownloadConfig downloadConfig() {
        return mConfig;
    }

    private void fetchDownloadItems() {
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_FETCH_DOWNLOAD_INFO).sendToTarget();
    }

    public void setGlobalDownloadListener(@NonNull DownloadListener downloadListener) {
        mGlobalDownloadListener = downloadListener;
    }

    public interface OnRedirectListener {
        String onRedirectUrl(String url);
    }

    public void setGlobalRedirectListener(@NonNull OnRedirectListener redirectListener) {
        mGlobalRedirectListener = redirectListener;
    }


    public void startDownload(VideoTaskItem taskItem) {
        startDownload(taskItem, null);
    }

    public void startDownload(VideoTaskItem taskItem, Map<String, String> headers) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;

        waitInit();
        synchronized (mQueueLock) {
            if (mVideoDownloadQueue.contains(taskItem)) {
                taskItem = mVideoDownloadQueue.getTaskItem(taskItem.getUrl());
            } else {
                taskItem = mVideoDownloadQueue.offer(taskItem);
                markDownloadInfoAddEvent(taskItem);
            }
        }
        int state = taskItem.getTaskState();
        switch (state) {
            case VideoTaskState.SUCCESS:
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_SUCCESS, taskItem).sendToTarget();
                break;
            case VideoTaskState.DOWNLOADING:
                //下载中,不做处理
                break;
            default:
                int downloadingCount = mVideoDownloadQueue.getDownloadingCount();
                if (downloadingCount < mConfig.getConcurrentCount()) {
                    taskItem.setTaskState(VideoTaskState.DOWNLOADING); //排队状态
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_START, taskItem).sendToTarget();
                    parseVideoDownloadInfo(taskItem, headers);
                } else {
                    taskItem.setTaskState(VideoTaskState.DEFAULT);//排队状态
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_DEFAULT, taskItem).sendToTarget();
                }
                break;
        }
    }

    private void parseVideoDownloadInfo(VideoTaskItem taskItem, Map<String, String> headers) {
        final String videoUrl = taskItem.getUrl();
        taskItem.setFileHash(VideoDownloadUtils.computeMD5(videoUrl));
        boolean taskExisted = taskItem.getDownloadCreateTime() != 0;
        if (taskExisted) {
            parseExistVideoDownloadInfo(taskItem, headers);
        } else {
            parseNetworkVideoInfo(taskItem, headers);
        }
    }

    private void parseExistVideoDownloadInfo(final VideoTaskItem taskItem, final Map<String, String> headers) {
        if (taskItem.isHlsType()) {
            VideoInfoParserManager.getInstance().parseLocalM3U8File(taskItem, new IVideoInfoParseListener() {
                @Override
                public void onM3U8FileParseSuccess(VideoTaskItem info, M3U8 m3u8) {
                    startM3U8VideoDownloadTask(taskItem, m3u8, headers);
                }

                @Override
                public void onM3U8FileParseFailed(VideoTaskItem info, Throwable error) {
                    parseNetworkVideoInfo(taskItem, headers);
                }
            });
        } else {
            startBaseVideoDownloadTask(taskItem, headers);
        }
    }

    private void parseNetworkVideoInfo(final VideoTaskItem taskItem, final Map<String, String> headers) {
        VideoInfoParserManager.getInstance().parseVideoInfo(taskItem, new IVideoInfoListener() {
            @Override
            public void onFinalUrl(String finalUrl) {
               if(DEBUG) Log.e("wzh", "onFinalUrl: " + finalUrl);
            }

            @Override
            public void onBaseVideoInfoSuccess(VideoTaskItem taskItem) {
                if(DEBUG) Log.e("wzh", "onBaseVideoInfoSuccess: " + taskItem);
                startBaseVideoDownloadTask(taskItem, headers);
            }

            @Override
            public void onBaseVideoInfoFailed(Throwable error) {
                LogUtils.w(TAG, "onInfoFailed error=" + error);
                if(DEBUG) Log.e("wzh", "onBaseVideoInfoFailed: ", error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoSuccess(VideoTaskItem info, M3U8 m3u8) {
                if(DEBUG)Log.e("wzh", "onM3U8InfoSuccess: " + info);
                taskItem.setMimeType(info.getMimeType());
                startM3U8VideoDownloadTask(taskItem, m3u8, headers);
            }

            @Override
            public void onLiveM3U8Callback(VideoTaskItem info) {
                if(DEBUG)Log.e("wzh", "onLiveM3U8Callback: " + info);
                LogUtils.w(TAG, "onLiveM3U8Callback cannot be cached.");
                taskItem.setErrorCode(DownloadExceptionUtils.LIVE_M3U8_ERROR);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public void onM3U8InfoFailed(Throwable error) {
                if(DEBUG) Log.e("wzh", "onM3U8InfoFailed: ", error);
                LogUtils.w(TAG, "onM3U8InfoFailed : " + error);
                int errorCode = DownloadExceptionUtils.getErrorCode(error);
                taskItem.setErrorCode(errorCode);
                taskItem.setTaskState(VideoTaskState.ERROR);
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
            }

            @Override
            public String calculateRealVideoUrl(String url) {
                if (mGlobalRedirectListener == null) {
                    return url;
                } else {
                    return mGlobalRedirectListener.onRedirectUrl(url);
                }
            }
        }, headers);
    }

    private void startM3U8VideoDownloadTask(final VideoTaskItem taskItem, M3U8 m3u8, Map<String, String> headers) {
//        taskItem.setTaskState(VideoTaskState.PREPARE);
//        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
        VideoTaskItem tempTaskItem = (VideoTaskItem) taskItem.clone();
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, tempTaskItem).sendToTarget();

        VideoDownloadTask downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        if (downloadTask == null) {
            downloadTask = new M3U8VideoDownloadTask(taskItem, m3u8, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        }
        startDownloadTask(downloadTask, taskItem);
    }

    private void startBaseVideoDownloadTask(VideoTaskItem taskItem, Map<String, String> headers) {
//        taskItem.setTaskState(VideoTaskState.PREPARE);
//        mVideoItemTaskMap.put(taskItem.getUrl(), taskItem);
        VideoTaskItem tempTaskItem = (VideoTaskItem) taskItem.clone();
        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, tempTaskItem).sendToTarget();

        VideoDownloadTask downloadTask = mVideoDownloadTaskMap.get(taskItem.getUrl());
        if (downloadTask == null) {
            downloadTask = new BaseVideoDownloadTask(taskItem, headers);
            mVideoDownloadTaskMap.put(taskItem.getUrl(), downloadTask);
        }
        startDownloadTask(downloadTask, taskItem);
    }

    private void startDownloadTask(VideoDownloadTask downloadTask, VideoTaskItem taskItem) {
        if (downloadTask != null) {
            downloadTask.setDownloadTaskListener(new IDownloadTaskListener() {
                @Override
                public void onTaskStart(String url) {
                    taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_START, taskItem).sendToTarget();
                }

                @Override
                public void onTaskProgress(float percent, long cachedSize, long totalSize, float speed) {
                    if (!taskItem.isPaused()) {
                        taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                        taskItem.setPercent(percent);
                        taskItem.setSpeed(speed);
                        taskItem.setDownloadSize(cachedSize);
                        taskItem.setTotalSize(totalSize);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskProgressForM3U8(float percent, long cachedSize, int curTs, int totalTs, float speed) {
                    if (!taskItem.isPaused()) {
                        taskItem.setTaskState(VideoTaskState.DOWNLOADING);
                        taskItem.setPercent(percent);
                        taskItem.setSpeed(speed);
                        taskItem.setDownloadSize(cachedSize);
                        taskItem.setCurTs(curTs);
                        taskItem.setTotalTs(totalTs);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PROCESSING, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskPaused() {
                    if (!taskItem.isErrorState() || !taskItem.isSuccessState()) {
                        taskItem.setTaskState(VideoTaskState.PAUSE);
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
                        mVideoDownloadHandler.removeMessages(DownloadConstants.MSG_DOWNLOAD_PROCESSING);
                    }
                }

                @Override
                public void onTaskFinished(long totalSize) {
                    if (taskItem.getTaskState() != VideoTaskState.SUCCESS) {
                        taskItem.setTaskState(VideoTaskState.SUCCESS);
                        taskItem.setDownloadSize(totalSize);
                        taskItem.setIsCompleted(true);
                        taskItem.setPercent(100f);
                        taskItem.setFileName(taskItem.makeFileName());
                        taskItem.setFilePath(taskItem.getSaveDir() + File.separator + taskItem.getFileName());
                        mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_SUCCESS, taskItem).sendToTarget();
                    }
                }

                @Override
                public void onTaskFailed(Throwable e) {
                    int errorCode = DownloadExceptionUtils.getErrorCode(e);
                    taskItem.setErrorCode(errorCode);
                    taskItem.setTaskState(VideoTaskState.ERROR);
                    mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_ERROR, taskItem).sendToTarget();
                }
            });

            downloadTask.startDownload();
        }
    }

    public String getDownloadPath() {
        if (mConfig != null) {
            return mConfig.getCacheRoot();
        }
        return null;
    }

    public void deleteAllVideoFiles() {
        try {
            waitInit();
            mVideoDownloadQueue.clear();
            VideoStorageUtils.clearVideoCacheDir();
//            mVideoItemTaskMap.clear();
            mVideoDownloadTaskMap.clear();
            mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DELETE_ALL_FILES).sendToTarget();
        } catch (Exception e) {
            LogUtils.w(TAG, "clearVideoCacheDir failed, exception = " + e.getMessage());
        }
    }

    public void pauseAllDownloadTasks() {
        waitInit();
        synchronized (mQueueLock) {
            List<VideoTaskItem> taskList = mVideoDownloadQueue.getDownloadList();
            for (VideoTaskItem taskItem : taskList) {
                 if (taskItem.isRunningTask()) {
                    pauseDownloadTask(taskItem);
                }
            }
        }
    }

    public void pauseDownloadTask(List<String> urlList) {
        waitInit();
        for (String url : urlList) {
            pauseDownloadTask(url);
        }
    }

    public void pauseDownloadTask(String videoUrl) {
        waitInit();
        pauseDownloadTask(mVideoDownloadQueue.getTaskItem(videoUrl));
    }

    public void pauseDownloadTask(VideoTaskItem taskItem) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getUrl()))
            return;
        String url = taskItem.getUrl();
        VideoDownloadTask task = mVideoDownloadTaskMap.remove(url);
        if (task != null) {
            task.pauseDownload();
        }else{
            taskItem.setTaskState(VideoTaskState.PAUSE);
            mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_PAUSE, taskItem).sendToTarget();
        }
    }

//    public void resumeDownload(String videoUrl) {
//        waitInit();
//        startDownload(mVideoDownloadQueue.getTaskItem(videoUrl));
//    }

    //Delete one task
    public void deleteVideoTask(VideoTaskItem taskItem, boolean shouldDeleteSourceFile) {
        waitInit();
        String cacheFilePath = getDownloadPath();
        if (!TextUtils.isEmpty(cacheFilePath)) {
            if (taskItem.isRunningTask()) {
                pauseDownloadTask(taskItem);
            }
            String saveName = VideoDownloadUtils.computeMD5(taskItem.getUrl());
            File file = new File(cacheFilePath + File.separator + saveName);
            mVideoDownloadQueue.remove(taskItem.getUrl());
            WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.deleteDownloadItemByUrl(taskItem));
            try {
                if (shouldDeleteSourceFile) {
                    VideoStorageUtils.delete(file);
                }
                mVideoDownloadTaskMap.remove(taskItem.getUrl());
                taskItem.reset();
                mVideoDownloadHandler.obtainMessage(DownloadConstants.MSG_DOWNLOAD_DEFAULT, taskItem).sendToTarget();
            } catch (Exception e) {
                LogUtils.w(TAG, "Delete file: " + file + " failed, exception=" + e.getMessage());
            }
        }
    }

    public void deleteVideoTask(String videoUrl, boolean shouldDeleteSourceFile) {
        waitInit();
        VideoTaskItem taskItem = mVideoDownloadQueue.remove(videoUrl);
        if (taskItem != null) {
            deleteVideoTask(taskItem, shouldDeleteSourceFile);
        }
    }

    public void deleteVideoTasks(List<String> urlList, boolean shouldDeleteSourceFile) {
        waitInit();
        for (String url : urlList) {
            deleteVideoTask(url, shouldDeleteSourceFile);
        }
    }

    public void deleteVideoTasks(VideoTaskItem[] taskItems, boolean shouldDeleteSourceFile) {
        waitInit();
        String cacheFilePath = getDownloadPath();
        if (!TextUtils.isEmpty(cacheFilePath)) {
            for (VideoTaskItem item : taskItems) {
                deleteVideoTask(item, shouldDeleteSourceFile);
            }
        }
    }

    private void nextDownloadQueue(VideoTaskItem taskItem) {
        waitInit();
        synchronized (mQueueLock) {
            LogUtils.w(TAG, "nextDownloadQueue size=" + mVideoDownloadQueue.size() + "," + mVideoDownloadQueue.getDownloadingCount() + "," + mVideoDownloadQueue.getPendingCount());
            int downloadingCount = mVideoDownloadQueue.getDownloadingCount();
            while (downloadingCount < mConfig.getConcurrentCount()) {
                VideoTaskItem item = mVideoDownloadQueue.peekPendingTask();
                if (item == null) {
                    break;
                }
                startDownload(item, null);
                downloadingCount++;
            }
        }
    }


    class VideoDownloadHandler extends Handler {

        public VideoDownloadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == DownloadConstants.MSG_FETCH_DOWNLOAD_INFO) {
                dispatchDownloadInfos();
            } else if (msg.what == DownloadConstants.MSG_DELETE_ALL_FILES) {
                //删除数据库中所有记录
                WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.deleteAllDownloadInfos());
            } else {
                dispatchDownloadMessage(msg.what, (VideoTaskItem) msg.obj);
            }
        }

        private void dispatchDownloadInfos() {
            WorkerThreadHandler.submitRunnableTask(() -> {
                try {
                    List<VideoTaskItem> taskItems = mVideoDatabaseHelper.getDownloadInfos();
                    for (VideoTaskItem taskItem : taskItems) {
                        mVideoDownloadQueue.addTask(taskItem);
                        if (mConfig != null && mConfig.shouldM3U8Merged() && taskItem.isHlsType()) {
                            doMergeTs(taskItem, taskItem1 -> {
                                markDownloadFinishEvent(taskItem1);
                            });
                        }
                    }
                    if (mInitCallbacks != null) {
                        mInitCallbacks.onDownloadInfos(true, null, mVideoDownloadQueue.getDownloadList());
                    }
                } finally {
                    synchronized (mInitialize) {
                        mInitialize.set(true);
                        mInitialize.notifyAll();
                    }
                }
            });
        }

        private void dispatchDownloadMessage(int msg, VideoTaskItem taskItem) {
            switch (msg) {
                case DownloadConstants.MSG_DOWNLOAD_DEFAULT:
                    handleOnDownloadDefault(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_START:
                    handleOnDownloadStart(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PROCESSING:
                    handleOnDownloadProcessing(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_PAUSE:
                    handleOnDownloadPause(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_ERROR:
                    handleOnDownloadError(taskItem);
                    break;
                case DownloadConstants.MSG_DOWNLOAD_SUCCESS:
                    handleOnDownloadSuccess(taskItem);
                    break;
            }
        }
    }

    private void handleOnDownloadDefault(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadPending(taskItem);
    }

    private void handleOnDownloadStart(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadStart(taskItem);
    }

    private void handleOnDownloadProcessing(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadProgress(taskItem);
        markDownloadProgressInfoUpdateEvent(taskItem);
    }

    private void handleOnDownloadPause(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadPause(taskItem);
        nextDownloadQueue(taskItem);
    }

    private void handleOnDownloadError(VideoTaskItem taskItem) {
        mGlobalDownloadListener.onDownloadError(taskItem);
        nextDownloadQueue(taskItem);
    }

    private void handleOnDownloadSuccess(VideoTaskItem taskItem) {
        nextDownloadQueue(taskItem);

        LogUtils.i(TAG, "handleOnDownloadSuccess shouldM3U8Merged=" + mConfig.shouldM3U8Merged() + ", isHlsType=" + taskItem.isHlsType());
        if (mConfig.shouldM3U8Merged() && taskItem.isHlsType()) {
            doMergeTs(taskItem, taskItem1 -> {
                mGlobalDownloadListener.onDownloadSuccess(taskItem1);
                markDownloadFinishEvent(taskItem1);
            });
        } else {
            mGlobalDownloadListener.onDownloadSuccess(taskItem);
            markDownloadFinishEvent(taskItem);
        }
    }

    private void doMergeTs(VideoTaskItem taskItem, @NonNull IM3U8MergeResultListener listener) {
        if (taskItem == null || TextUtils.isEmpty(taskItem.getFilePath())) {
            listener.onCallback(taskItem);
            return;
        }
        LogUtils.i(TAG, "VideoMerge doMergeTs taskItem=" + taskItem);
        String inputPath = taskItem.getFilePath();
        if (TextUtils.isEmpty(taskItem.getFileHash())) {
            taskItem.setFileHash(VideoDownloadUtils.computeMD5(taskItem.getUrl()));
        }
        final String outName = taskItem.getTitle() + ".mp4";
        final String outputPath = inputPath.substring(0, inputPath.lastIndexOf("/")) + File.separator + outName;
        File outputFile = new File(outputPath);
        if (outputFile.exists()) {
            outputFile.delete();
        }
        LogUtils.i(TAG, "doMergeTs: " + inputPath + " > " + outputPath);
        VideoProcessManager.getInstance().mergeTs(inputPath, outputPath, new IM3U8MergeListener() {
            @Override
            public void onMergedFinished() {
                LogUtils.i(TAG, "VideoMerge onMergedFinished outputPath=" + outputPath);
                taskItem.setFileName(outName);
                taskItem.setFilePath(outputPath);
                taskItem.setMimeType(Video.Mime.MIME_TYPE_MP4);
                taskItem.setVideoType(Video.Type.MP4_TYPE);
                listener.onCallback(taskItem);

                //delete source file
                File outputFile = new File(outputPath);
                File[] files = outputFile.getParentFile().listFiles();
                for (File subFile : files) {
                    String subFilePath = subFile.getAbsolutePath();
                    if (!subFilePath.endsWith(outName)) {
                        subFile.delete();
                    }
                }
            }

            @Override
            public void onMergeFailed(Exception e) {
                LogUtils.i(TAG, "VideoMerge onMergeFailed e=" + e);
                File outputFile = new File(outputPath);
                if (outputFile.exists()) {
                    outputFile.delete();
                }

                listener.onCallback(taskItem);
            }
        });
    }

    private void markDownloadInfoAddEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadInfoAddEvent(taskItem));
    }

    private void markDownloadProgressInfoUpdateEvent(VideoTaskItem taskItem) {
        long currentTime = System.currentTimeMillis();
        if (taskItem.getLastUpdateTime() + 1000 < currentTime) {
            WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem));
            taskItem.setLastUpdateTime(currentTime);
        }
    }

    private void markDownloadFinishEvent(VideoTaskItem taskItem) {
        WorkerThreadHandler.submitRunnableTask(() -> mVideoDatabaseHelper.markDownloadProgressInfoUpdateEvent(taskItem));
    }
}
