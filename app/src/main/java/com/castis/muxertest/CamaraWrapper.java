package com.castis.muxertest;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class CamaraWrapper implements SurfaceHolder.Callback {

    private final String TAG = "VideoEncoderFromBuffer";
    public final static int VWIDTH = 640;

    public final static int VHEIGHT = 480;

    private Camera camera = null;

    private Camera.Size vSize;

    private byte[] callbackBuffer;

    private CameraPreviewCallback cameraPreviewCallback;

    private MediaMuxer mMuxer;

    private Encoder encoder;

    private VideoEncoderFromBuffer videoEncoderFromBuffer;

    private AudioEncoderFromBuffer audioEncoderFromBuffer;

    private long mStartTime = 0;

    private int videoIndex = -1, audioIndex = -1;

    public boolean isStop = false;

    public CamaraWrapper() {
    }

    public Camera open(int cameraId, int imageFormat, SurfaceHolder holder) {
        camera = Camera.open(cameraId);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setPreviewFormat(imageFormat);
        parameters.setRotation(180);
        parameters.set("orientation", "portrait"); // landscape

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

        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(this);

        cameraPreviewCallback = new CameraPreviewCallback();

        encoder = new Encoder() {
            @Override
            public void writeSampleData(int trackIndex, ByteBuffer writeByteBuffer, MediaCodec.BufferInfo bufferInfo) {
                Log.i(TAG, "Encoder writeSampleData trackIndex : " + trackIndex
                        + " / writeByteBuffer : " + writeByteBuffer.toString()
                        + " / bufferInfo : " + bufferInfo.toString());
                mMuxer.writeSampleData(trackIndex, writeByteBuffer, bufferInfo);
            }

            @Override
            public MediaMuxer getMuxer() {
                return mMuxer;
            }
        };

        isStop = true;

        Log.i(TAG, "oenEnd");
        return camera;
    }

    public void build() {
        mStartTime = System.nanoTime();
//        audioEncoderFromBuffer = new AudioEncoderFromBuffer(mStartTime);
        videoEncoderFromBuffer = new VideoEncoderFromBuffer(mStartTime, encoder, vSize);

        File file = Util.getOutputMediaFile("h264", "mp4");
        Log.i(TAG, "Output FileName : " + file.getName());

        try {
            mMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

//        audioEncoderFromBuffer.setTrackIndex(mMuxer.addTrack(audioEncoderFromBuffer.getAudioCodec().getOutputFormat()));
        videoEncoderFromBuffer.setTrackIndex(mMuxer.addTrack(videoEncoderFromBuffer.getaMediaCodec().getOutputFormat()));
        Camera.Parameters parameters = camera.getParameters();
        callbackBuffer = null;
        callbackBuffer = new byte[parameters.getPreviewSize().width *
                parameters.getPreviewSize().height *
                ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
        camera.addCallbackBuffer(callbackBuffer);
        camera.setPreviewCallbackWithBuffer(cameraPreviewCallback);
        isStop = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated============");
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
//            vSize.width = width;
//            vSize.height = height;

//            camera.setPreviewCallback(cameraPreviewCallback);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
    }

    class CameraPreviewCallback implements Camera.PreviewCallback {

        public void close() {
            isStop = true;
            videoEncoderFromBuffer.close();

            camera.addCallbackBuffer(null);
            camera.setPreviewCallbackWithBuffer(null);

            try {
                Log.i(TAG, "mMuxer close()");
                mMuxer.stop();
                mMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.i(TAG, "onPreviewFrame " + data.toString());

            Camera.Parameters parameters = camera.getParameters();
            int format = parameters.getPreviewFormat();

            Log.i(TAG, "format : " + Integer.toString(format, 16));

            long startTime = System.currentTimeMillis();
            if (!isStop)
                videoEncoderFromBuffer.encodeFrame(data/*, encodeData*/);

            long endTime = System.currentTimeMillis();
            Log.i(TAG, Integer.toString((int) (endTime - startTime)) + "ms");
            camera.addCallbackBuffer(data);
        }
    }

    public void close() {
        isStop = true;
        cameraPreviewCallback.close();
    }

    public Camera.Size getSize() {
        return vSize;
    }

    public CameraPreviewCallback getPreviewCallback() {
        return cameraPreviewCallback;
    }

}
