package com.castis.muxertest.origin;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.SurfaceHolder;

import com.castis.muxertest.AudioEncoderFromBuffer;
import com.castis.muxertest.Util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class CamaraWrapper2 {

    private final String TAG = "VideoEncoderFromBuffer";
    public final static int VWIDTH = 640;
    public final static int VHEIGHT = 480;

    private Context context;

    private Camera camera = null;

    private Camera.Size vSize;

    private byte[] callbackBuffer;

    private CameraPreviewCallback cameraPreviewCallback;

    private EncoderCallback encoderCallback;

    private MediaMuxer muxer;

    VideoEncoderFromBuffer2 fromBuffer2;

    AudioEncoderFromBuffer2 afromBuffer2;

    public CamaraWrapper2() {
    }

    public Camera open(int cameraId, int imageFormat, SurfaceHolder holder) {
        long mStartTime = System.nanoTime();

        camera = Camera.open(cameraId);
        Camera.Parameters parameters = camera.getParameters();

        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setPreviewFormat(imageFormat);
        parameters.set("orientation", "portrait"); //landscape
        parameters.setRotation(180);

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

        File file = Util.getOutputMediaFile("h264", "mp4");
        Log.i(TAG, "Output FileName : " + file.getName());
        try {
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }


        cameraPreviewCallback = new CameraPreviewCallback();
        encoderCallback = new EncoderCallback() {
            int cnt = 0, stopCnt = 0, release = 0;

            @Override
            public void start() {
                cnt++;
                Log.d(TAG, "encoderCallback cnt : " + cnt);
                if (cnt > 1) {
                    muxer.start();
                }
            }

            @Override
            public void stop() {
                stopCnt++;
                if (stopCnt > 1) {
                    muxer.stop();
                    stopCnt = 0;
                    cnt = 0;
                }
            }

            @Override
            public void release() {
                release++;
                if (release > 1) {
                    muxer.release();
                    release = 0;
                }
            }

            @Override
            public int addTrack(MediaFormat format) {
                if (muxer != null)
                    return muxer.addTrack(format);
                return -1;
            }

            @Override
            public void writeSampleData(int trackIndex, ByteBuffer writeByteBuffer, MediaCodec.BufferInfo bufferInfo) {
                Log.d(TAG, "writeSampleData trackIndex : " +
                        trackIndex + " / writeByteBuffer : " +
                        writeByteBuffer.toString() + " / bufferInfo : " +
                        bufferInfo.toString());
                Log.d(TAG, "encoderCallback cnt : " + cnt);
                if (cnt > 1)
                    muxer.writeSampleData(trackIndex, writeByteBuffer, bufferInfo);
            }
        };

        fromBuffer2 = new VideoEncoderFromBuffer2(vSize, mStartTime);
        fromBuffer2.setMuxerCallback(encoderCallback);

        afromBuffer2 = new AudioEncoderFromBuffer2(mStartTime);
        afromBuffer2.setMuxerCallback(encoderCallback);

        Log.i(TAG, "oenEnd");
        return camera;
    }

    class CameraPreviewCallback implements Camera.PreviewCallback {
        public boolean isStop = false;

        public CameraPreviewCallback() {

        }

        public void close() {
            isStop = true;
            fromBuffer2.close();
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.i(TAG, "onPreviewFrame " + data.toString());
            fromBuffer2.encodeFrame(data);
            camera.addCallbackBuffer(data);
        }
    }

    public void close() {
        cameraPreviewCallback.close();
    }

    public Camera.Size getSize() {
        return vSize;
    }

    public CameraPreviewCallback getPreviewCallback() {
        return cameraPreviewCallback;
    }
}
