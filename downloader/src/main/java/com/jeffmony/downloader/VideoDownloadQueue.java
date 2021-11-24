package com.jeffmony.downloader;

import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.utils.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom Download Queue.
 */
public class VideoDownloadQueue {

    private static final String TAG = "VideoDownloadQueue";

    private List<VideoTaskItem> mQueue;
    private Map<String, VideoTaskItem> mTasks;

    public VideoDownloadQueue() {
        mQueue = new ArrayList<>();
        mTasks = new HashMap<>();
    }

    public List<VideoTaskItem> getDownloadList() {
        synchronized (this) {
            return new ArrayList<>(mQueue);
        }
    }

    public void clear() {
        synchronized (this) {
            mQueue.clear();
            mTasks.clear();
        }

    }

    public VideoTaskItem remove(String url) {
        VideoTaskItem task = null;
        synchronized (this) {
            task = mTasks.remove(url);
            if (task != null) {
                mQueue.remove(task);
            }
        }
        return task;
    }

    /**
     * 用于从数据库读取恢复下载队列
     * @param taskItem
     */
    void addTask(VideoTaskItem taskItem) {
        synchronized (this) {
            VideoTaskItem task = mTasks.get(taskItem.getUrl());
            if (task == null) {
                mTasks.put(taskItem.getUrl(), taskItem);
                mQueue.add(taskItem);
            }
        }
    }

    //put it into queue
    public VideoTaskItem offer(VideoTaskItem taskItem) {
        synchronized (this) {
            VideoTaskItem task = mTasks.get(taskItem.getUrl());
            if (task == null) {
                task = (VideoTaskItem) taskItem.clone();
                task.setTaskState(VideoTaskState.DEFAULT);
//                task.setDownloadCreateTime(System.currentTimeMillis());

                mTasks.put(taskItem.getUrl(), task);
                mQueue.add(task);
            }
            return task;
        }
    }

//    //Remove Queue head item,
//    //Return Next Queue head.
//    public VideoTaskItem poll() {
//        try {
//            if (mQueue.size() >= 2) {
//                mQueue.remove(0);
//                return mQueue.get(0);
//            } else if (mQueue.size() == 1) {
//                mQueue.remove(0);
//            }
//        } catch (Exception e) {
//            LogUtils.w(TAG, "DownloadQueue remove failed.");
//        }
//        return null;
//    }
//
//    public VideoTaskItem peek() {
//        try {
//            if (mQueue.size() >= 1) {
//                return mQueue.get(0);
//            }
//        } catch (Exception e) {
//            LogUtils.w(TAG, "DownloadQueue get failed.");
//        }
//        return null;
//    }

    public boolean remove(VideoTaskItem taskItem) {
        remove(taskItem.getUrl());
        return false;
    }

    public boolean contains(VideoTaskItem taskItem) {
        synchronized (this) {
            return mTasks.containsKey(taskItem.getUrl());
        }
    }

    public VideoTaskItem getTaskItem(String url) {
        synchronized (this) {
            return mTasks.get(url);
        }
    }

    public boolean isEmpty() {
        synchronized (this) {
            return mTasks.isEmpty();
        }
    }

    public int size() {
        synchronized (this) {
            return mQueue.size();
        }
    }

//    public boolean isHead(VideoTaskItem taskItem) {
//        if (taskItem == null)
//            return false;
//        return taskItem.equals(peek());
//    }

    public int getDownloadingCount() {
        synchronized (this) {
            int count = 0;
            for (VideoTaskItem task : mQueue) {
                if (isTaskRunnig(task)) {
                    count++;
                }
            }
            return count;
        }
    }

    public int getPendingCount() {
        synchronized (this) {
            int count = 0;
            for (VideoTaskItem task : mQueue) {
                if (isTaskPending(task)) {
                    count++;
                }
            }
            return count;
        }
    }

    public VideoTaskItem peekPendingTask() {
        synchronized (this) {
            for (VideoTaskItem task : mQueue) {
                if (isTaskPending(task)) {
                    return task;
                }
            }
        }
        return null;
    }

    public boolean isTaskPending(VideoTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return taskState == VideoTaskState.DEFAULT;
    }

    public boolean isTaskRunnig(VideoTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return taskState == VideoTaskState.DOWNLOADING;
    }
}
