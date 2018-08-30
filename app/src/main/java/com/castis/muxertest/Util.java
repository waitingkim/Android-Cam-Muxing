package com.castis.muxertest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Util {

    private static final String TAG = "VideoEncoderFromBuffer";


    public static File getOutputMediaFile(String child, String fileName) {
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
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + fileName);

        return mediaFile;
    }

    public static void SaveBitmapToFileCache(Bitmap bitmap, String strFilePath) {

        File fileCacheItem = new File(strFilePath);
        OutputStream out = null;

        try
        {
            fileCacheItem.createNewFile();
            out = new FileOutputStream(fileCacheItem);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                out.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    public static Bitmap decodeNV21(byte[] data, Camera.Parameters ps){
        Bitmap retimage = null;

        if(ps.getPreviewFormat() == ImageFormat.NV21 /* || YUV2, NV16 */){
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

        }else if(ps.getPreviewFormat() == ImageFormat.JPEG || ps.getPreviewFormat() == ImageFormat.RGB_565){
            retimage = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return retimage;
    }




}