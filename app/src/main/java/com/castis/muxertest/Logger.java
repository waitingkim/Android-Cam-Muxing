package com.castis.muxertest;

import android.os.Debug;
import android.util.Log;

import java.text.DecimalFormat;

public class Logger {

    public static String CastisTag = "KT.BOM.LSA";

    public static void Log(int logType, Object object, String message) {
        switch (logType) {
            case Log.INFO:
                i(object, message);
                break;
            case Log.DEBUG:
                d(object, message);
                break;
            case Log.ERROR:
                e(object, message);
                break;
            case Log.VERBOSE:
                v(object, message);
                break;
            case Log.WARN:
                w(object, message);
                break;
            default:
                w(object, "Not Type" + message);
                break;
        }
    }

    public static void Log(int logType, Object object, String message, Throwable tr) {
        switch (logType) {
            case Log.INFO:
                i(object, message, tr);
                break;
            case Log.DEBUG:
                d(object, message, tr);
                break;
            case Log.ERROR:
                e(object, message, tr);
                break;
            case Log.VERBOSE:
                v(object, message, tr);
                break;
            case Log.WARN:
                w(object, message, tr);
                break;
            default:
                w(object, "Not Type" + message, tr);
                break;
        }
    }

    public static void i(Object object, String message) {
        Log.i(CastisTag, getClassName(object) + message);
    }

    public static void i(Object object, String message, Throwable tr) {
        Log.e(CastisTag, getClassName(object) + message, tr);
    }

    public static void d(Object object, String message) {
        Log.d(CastisTag, getClassName(object) + message);
    }

    public static void d(Object object, String message, Throwable tr) {
        Log.e(CastisTag, getClassName(object) + message, tr);
    }

    public static void e(Object object, String message) {
        Log.e(CastisTag, getClassName(object) + message);
    }

    public static void e(Object object, String message, Throwable tr) {
        Log.e(CastisTag, getClassName(object) + message, tr);
    }

    public static void w(Object object, String message) {
        Log.w(CastisTag, getClassName(object) + message);
    }

    public static void w(Object object, String message, Throwable tr) {
        Log.e(CastisTag, getClassName(object) + message, tr);
    }

    public static void v(Object object, String message) {
        Log.v(CastisTag, getClassName(object) + message);
    }

    public static void v(Object object, String message, Throwable tr) {
        Log.e(CastisTag, getClassName(object) + message, tr);
    }

    public static void heapLog() {
        Double allocated = new Double(Debug.getNativeHeapAllocatedSize()) / new Double((1048576));
        Double available = new Double(Debug.getNativeHeapSize()) / 1048576.0;
        Double free = new Double(Debug.getNativeHeapFreeSize()) / 1048576.0;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);

        Log.d("Castis", "[ks] heap native: allocated " + df.format(allocated) + "MB of " + df.format(available) + "MB (" + df.format(free) + "MB free)");
        Log.d("Castis",
                "[ks] memory: allocated: " + df.format(new Double(Runtime.getRuntime().totalMemory() / 1048576)) + "MB of "
                        + df.format(new Double(Runtime.getRuntime().maxMemory() / 1048576)) + "MB ("
                        + df.format(new Double(Runtime.getRuntime().freeMemory() / 1048576)) + "MB free)");
        System.gc();
    }

    /**
     * 클래스 파일이름 가져오기
     */
    public static String getClassName(Object obj) {
        String simpleName = "";
        if (obj instanceof String) {
            simpleName = (String) obj;
        } else {
            simpleName = obj.getClass().getSimpleName();
        }
        return "[" + simpleName + "] ";
    }

}
