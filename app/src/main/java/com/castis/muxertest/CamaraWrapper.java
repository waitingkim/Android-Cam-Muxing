package com.castis.muxertest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.util.List;

public class CamaraWrapper implements SurfaceHolder.Callback {

    private final String TAG = "VideoEncoderFromBuffer";
    private final int MY_PERMISSION_REQUEST_STORAGE = 100;
    public final static int VWIDTH = 640;
    public final static int VHEIGHT = 480;

    private Context context;

    private Camera camera = null;

    private Camera.Size vSize;

    private byte[] callbackBuffer;

    private CameraPreviewCallback cameraPreviewCallback;

    public CamaraWrapper(Context c) {
        this.context = c;
        checkPermission();
    }

    /**
     * Permission check.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        Log.i(TAG, "CheckPermission : " + context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (((MainActivity) context).shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(context, "Read/Write external storage", Toast.LENGTH_SHORT).show();
            }

            ((MainActivity) context).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSION_REQUEST_STORAGE);
        } else {
            Log.e(TAG, "permission deny");
        }
    }

    public Camera open(int cameraId, int imageFormat, SurfaceHolder holder) {
        camera = Camera.open(cameraId);
        Camera.Parameters parameters = camera.getParameters();

        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setPreviewFormat(imageFormat);
        parameters.set("orientation", "portrait");
//        parameters.set("orientation", "landscape");
        parameters.setRotation(180);

        //parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

        Camera.Size size = null;

        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        for (int i = 0; i < sizes.size(); i++) {
            Camera.Size s = sizes.get(i);
            Log.i(TAG, String.format("camera supported picture size %dx%d", s.width, s.height));
            if (size == null) {
                if (s.height == VHEIGHT) {
                    size = s;
                }
            } else {
                if (s.width == VWIDTH) {
                    size = s;
                }
            }
        }

        size.height = VHEIGHT;
        size.width = VWIDTH;

        vSize = size;

        parameters.setPreviewSize(size.width, size.height);
        camera.setParameters(parameters);

//        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        holder.addCallback(this);

        cameraPreviewCallback = new CameraPreviewCallback();

        Log.i(TAG, "oenEnd");

        return camera;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        try {
            camera.unlock();
            camera.reconnect();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged format : " + Integer.toString(format, 16) + " / width : " + width + " / height : " + height);
        if (camera != null) {
            vSize.width = width;
            vSize.height = height;
            Camera.Parameters parameters = camera.getParameters();
            callbackBuffer = null;
            callbackBuffer = new byte[parameters.getPreviewSize().width *
                    parameters.getPreviewSize().height *
                    ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
            camera.addCallbackBuffer(callbackBuffer);
            camera.setPreviewCallbackWithBuffer(cameraPreviewCallback);
//            camera.setPreviewCallback(cameraPreviewCallback);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
    }

    class CameraPreviewCallback implements Camera.PreviewCallback {

        public boolean isStop = false;

        private VideoEncoderFromBuffer videoEncoder = null;

        public CameraPreviewCallback() {
            videoEncoder = new VideoEncoderFromBuffer(vSize);
        }

        public void close() {
            isStop = true;
            videoEncoder.close();
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.i(TAG, "onPreviewFrame " + data.toString());
            if (isStop)
                return;

            Camera.Parameters parameters = camera.getParameters();
            int format = parameters.getPreviewFormat();
//            ImageFormat

            Log.i(TAG, "format : " + Integer.toString(format, 16));

            /*Bitmap bm = Util.decodeNV21(data, parameters);
            File file = Util.getOutputMediaFile("bitmpa", "bitmap.jpg");
            Util.SaveBitmapToFileCache(bm, file.getAbsolutePath());*/

            long startTime = System.currentTimeMillis();
            videoEncoder.encodeFrame(data/*, encodeData*/);
//            videoEncoder.encodeFrame(data, parameters);
            long endTime = System.currentTimeMillis();
            Log.i(TAG, Integer.toString((int) (endTime - startTime)) + "ms");
            camera.addCallbackBuffer(data);
        }
    }

    public void close() {
        cameraPreviewCallback.close();
    }

    public Camera.Size getSize(){
        return vSize;
    }

    public CameraPreviewCallback getPreviewCallback(){
        return cameraPreviewCallback;
    }

}
