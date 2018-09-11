package com.castis.muxertest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.castis.muxertest.origin.CamaraWrapper2;
import com.castis.muxertest.origin.Main2Activity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Util {

    private static final String TAG = "VideoEncoderFromBuffer";


    public static File getOutputMediaFile(String child, long pts, String fileName) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), child);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {

            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMddmmss", Locale.US).format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + "_" + pts + fileName);

        return mediaFile;
    }

    public static void SaveBitmapToFileCache(Bitmap bitmap, String strFilePath) {
        File fileCacheItem = new File(strFilePath);
        OutputStream out = null;

        try {
            fileCacheItem.createNewFile();
            out = new FileOutputStream(fileCacheItem);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static Bitmap decodeNV21(byte[] data, Camera.Parameters ps) {
        Bitmap retimage = null;

        if (ps.getPreviewFormat() == ImageFormat.NV21 /* || YUV2, NV16 */) {
            int w = ps.getPreviewSize().width;
            int h = ps.getPreviewSize().height;
            //Get the YUV image
            YuvImage yuv_image = new YuvImage(data, ps.getPreviewFormat(), w, h, null);
            //Convert Yuv to Jpeg
            Rect rect = new Rect(0, 0, w, h);
            ByteArrayOutputStream out_stream = new ByteArrayOutputStream();
            yuv_image.compressToJpeg(rect, 100, out_stream); // 따라가보면 JNI를 사용하여 처리한다.
            //Convert Yuv to jpeg
            retimage = BitmapFactory.decodeByteArray(out_stream.toByteArray(), 0, out_stream.size());

        } else if (ps.getPreviewFormat() == ImageFormat.JPEG || ps.getPreviewFormat() == ImageFormat.RGB_565) {
            retimage = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return retimage;
    }

    /**
     * Permission check.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public static void checkPermission(Context context, int requestCode) {
        Log.i(TAG, "CheckPermission : " + context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (((Main2Activity) context).shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(context, "Read/Write external storage", Toast.LENGTH_SHORT).show();
            }

            ((Main2Activity) context).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET},
                    requestCode);
        } else {
            Log.e(TAG, "permission deny");
        }
    }

    public static byte[] framePacketizing(int src, int size) {
        BigInteger bigInt = BigInteger.valueOf(src);
        return framePacketizing(bigInt, size);
    }

    public static byte[] framePacketizing(long src, int size) {
        BigInteger bigInt = BigInteger.valueOf(src);
        return framePacketizing(bigInt, size);
    }

    private static byte[] framePacketizing(BigInteger bigInt, int size) {
       /* byte[] dest = new byte[size];
        int length = bigInt.toByteArray().length;
        System.arraycopy(bigInt.toByteArray(), 0, dest, 0, length);
//        Logger.i("Util", "dest.length : " + dest.length + " / src.length : "+ length);
        return reverse(dest);*/

        byte[] tIdBytes = reverse(bigInt.toByteArray());
        byte[] dest = ByteBuffer.allocate(size).put(tIdBytes).array();
        return dest;
    }

    public static byte[] reverse(byte[] objects) {
        byte[] temp = new byte[objects.length];
        for (int left = 0, right = objects.length - 1; left <= right; left++, right--) {
            temp[left] = objects[right];
            temp[right] = objects[left];
        }
        return temp;
    }

}
