package com.jeffmony.videodemo.download;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.downloader.VideoDownloadManager;
import com.jeffmony.downloader.listener.DownloadListener;
import com.jeffmony.downloader.listener.IDownloadInitCallback;
import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.model.VideoTaskState;
import com.jeffmony.downloader.utils.LogUtils;
import com.jeffmony.videodemo.R;

import java.util.ArrayList;
import java.util.List;

public class VideoDownloadListActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "wzh2";//"DownloadFeatureActivity";

    private Button mPauseAllBtn;
    private Button mStartAllBtn;
    private ListView mDownloadListView;

    private VideoDownloadListAdapter mAdapter;
    private List<VideoTaskItem> items = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);
        mPauseAllBtn = findViewById(R.id.pause_task_btn);
        mStartAllBtn = findViewById(R.id.start_task_btn);
        mDownloadListView = findViewById(R.id.download_listview);
        mStartAllBtn.setOnClickListener(this);
        mPauseAllBtn.setOnClickListener(this);
        VideoDownloadManager.getInstance().setGlobalDownloadListener(mListener);
        initDatas();
    }

    private void initDatas() {
//        VideoTaskItem item1 = new VideoTaskItem("https://v3.dious.cc/20201224/v04Vp1ES/index.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test1");
        VideoTaskItem item1 = new VideoTaskItem("https://myflixer.to/watch-movie/david-byrnes-american-utopia-63834.3495477", null, "test1");
        VideoTaskItem item2 = new VideoTaskItem("https://v3.dious.cc/20201224/6Q1yAHRu/index.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test2");
        VideoTaskItem item3 = new VideoTaskItem("https://v3.dious.cc/20201224/aQKzuq6G/index.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test3");
        VideoTaskItem item4 = new VideoTaskItem("https://v3.dious.cc/20201224/WWTyUxS6/index.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test3");
        VideoTaskItem item5 = new VideoTaskItem("http://videoconverter.vivo.com.cn/201706/655_1498479540118.mp4.main.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test4");
        VideoTaskItem item6 = new VideoTaskItem("https://europe.olemovienews.com/hlstimeofffmp4/20210226/fICqcpqr/mp4/fICqcpqr.mp4/master.m3u8", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test5");
        VideoTaskItem item7 = new VideoTaskItem("https://rrsp-1252816746.cos.ap-shanghai.myqcloud.com/0c1f023caa3bbefbe16a5ce564142bbe.mp4", "https://i.loli.net/2021/04/18/WuAUZc85meB6D2Q.jpg", "test6");

        items.add(item1);
        items.add(item2);
        items.add(item3);
        items.add(item4);
        items.add(item5);
        items.add(item6);
        items.add(item7);

        mAdapter = new VideoDownloadListAdapter(this, R.layout.download_item, items);
        mDownloadListView.setAdapter(mAdapter);

        List<VideoTaskItem> list = VideoDownloadManager.getInstance().getAllTaskItems();
        items.addAll(list);
        for (VideoTaskItem item : list) {
            notifyChanged(item);
        }
        VideoDownloadManager.getInstance().setGlobalRedirectListener(new VideoDownloadManager.OnRedirectListener() {
            @Override
            public String onRedirectUrl(String url) {
                return "https://b-g-eu-1.betterstream.co:2222/v2-hls-playback/73355e1315b5fdc780d19651e8c99dfd4d92f06d3e8cfbd414d4a50c38344b5752620175885cb985dc87b388b7fdfe720d3c4efe11f3bfd6dde18ad470311e9121f605ea591800d83f8d0e0c2d2ef04e0fc437f46ca6b7837b16e0d8003db2fe11050c3834a040ca6c970d86394278ff16836958c5bcafb8e0bade1f4ef17f3f141565c921d9502095b456688dcbeaf6331f3fb27c6c64fefb31c8729541678e/720/index.m3u8";
            }
        });
        mDownloadListView.setOnItemClickListener((parent, view, position, id) -> {
            VideoTaskItem item = items.get(position);
            int state = item.getTaskState();
            switch (state) {
                case VideoTaskState.DEFAULT:
                case VideoTaskState.PENDING:
                case VideoTaskState.ERROR:
                case VideoTaskState.PAUSE:
                    VideoDownloadManager.getInstance().startDownload(item);
                    break;
                case VideoTaskState.SUCCESS:
                    break;
                case VideoTaskState.PREPARE:
                case VideoTaskState.START:
                case VideoTaskState.DOWNLOADING:
                    VideoDownloadManager.getInstance().pauseDownloadTask(item.getUrl());
                    break;
            }
        });
    }


    private long mLastProgressTimeStamp;
    private long mLastSpeedTimeStamp;

    private DownloadListener mListener = new DownloadListener() {

        @Override
        public void onDownloadDefault(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadDefault: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadPending(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadPending: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadPrepare(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadPrepare: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadStart(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadStart: " + item);
            notifyChanged(item);
        }

        @Override
        public void onDownloadProgress(VideoTaskItem item) {
            long currentTimeStamp = System.currentTimeMillis();
            if (currentTimeStamp - mLastProgressTimeStamp > 1000) {
                LogUtils.w(TAG, "onDownloadProgress: " + item.getPercentString() + ", curTs=" + item.getCurTs() + ", totalTs=" + item.getTotalTs());
                notifyChanged(item);
                mLastProgressTimeStamp = currentTimeStamp;
            }
        }

        @Override
        public void onDownloadSpeed(VideoTaskItem item) {
            long currentTimeStamp = System.currentTimeMillis();
            if (currentTimeStamp - mLastSpeedTimeStamp > 1000) {
                notifyChanged(item);
                mLastSpeedTimeStamp = currentTimeStamp;
            }
        }

        @Override
        public void onDownloadPause(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadPause: " + item.getUrl());
            notifyChanged(item);
        }

        @Override
        public void onDownloadError(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadError: " + item.getUrl());
            notifyChanged(item);
        }

        @Override
        public void onDownloadSuccess(VideoTaskItem item) {
            LogUtils.w(TAG, "onDownloadSuccess: " + item);
            notifyChanged(item);
        }
    };

    private void notifyChanged(final VideoTaskItem item) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyChanged(items, item);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mStartAllBtn) {

        } else if (v == mPauseAllBtn) {
            VideoDownloadManager.getInstance().pauseAllDownloadTasks();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
