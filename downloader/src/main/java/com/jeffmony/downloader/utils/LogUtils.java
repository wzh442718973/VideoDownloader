package com.jeffmony.downloader.utils;

import android.util.Log;

public class LogUtils {

    public static final int LOG_DEBUG = 2;
    public static final int LOG_INFO = 3;
    public static final int LOG_WARN = 4;
    public static final int LOG_ERROR = 5;

    private static int sLogLevel = LOG_ERROR;

    public static void setLogLevel(int level) {
        sLogLevel = level;
    }

    public static void d(String tag, String msg) {
        if (sLogLevel <= LOG_DEBUG)
            Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (sLogLevel <= LOG_INFO)
            Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (sLogLevel <= LOG_WARN)
            Log.w(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (sLogLevel <= LOG_ERROR)
            Log.e(tag, msg);
    }

}
