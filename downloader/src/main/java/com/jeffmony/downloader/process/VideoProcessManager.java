package com.jeffmony.downloader.process;

import androidx.annotation.NonNull;

//import com.jeffmony.downloader.utils.LogUtils;
//import com.jeffmony.ffmpeglib.FFmpegVideoUtils;
import com.jeffmony.m3u8library.listener.IVideoTransformListener;

import java.io.File;

public class VideoProcessManager {

    private static final String TAG = "VideoProcessManager";

    public static VideoProcessManager sInstance = null;

    public static VideoProcessManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProcessManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProcessManager();
                }
            }
        }
        return sInstance;
    }

    public void mergeTs(String inputPath, String outputPath, @NonNull IM3U8MergeListener listener) {
//        int result = FFmpegVideoUtils.transformVideo(inputPath, outputPath);
//        LogUtils.i(TAG, "VideoMerge mergeTs result=" +result);
//        if (result < 0) {
//            listener.onMergeFailed(new Exception("Merge ts failed"));
//        } else {
//            File outputFile = new File(outputPath);
//            if (outputFile.exists()) {
//                listener.onMergedFinished();
//            } else {
//                listener.onMergeFailed(new Exception("Merge ts failed, No file"));
//            }
//        }

        com.jeffmony.m3u8library.VideoProcessManager.getInstance().transformM3U8ToMp4(inputPath, outputPath, new IVideoTransformListener() {
            @Override
            public void onTransformProgress(float progress) {

            }

            @Override
            public void onTransformFailed(Exception e) {
                listener.onMergeFailed(new Exception("Merge ts failed", e));
            }

            @Override
            public void onTransformFinished() {
                File outputFile = new File(outputPath);
                if (outputFile.exists()) {
                    listener.onMergedFinished();
                } else {
                    listener.onMergeFailed(new Exception("Merge ts failed, No file"));
                }
            }
        });
    }

    public void printVideoInfo(String srcPath) {
//        FFmpegVideoUtils.printVideoInfo(srcPath);
    }

}
