package com.castis.muxertest;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    // video device.
    public final static int VWIDTH = 640;
    public final static int VHEIGHT = 480;
    private Camera.Size vsize;

    String TAG = "VideoEncoderFromBuffer";

    SurfaceView videoView;

    Button btnStart, btnEnd;

    CamaraWrapper camaraWrapper;

    Context context;

    SurfaceHolder surfaceHolder;

    MediaPrepareTask mediaPrepareTask = new MediaPrepareTask();

    Camera camera = null;

    Camera.Size size = null;

    private byte[] callbackBuffer;

    Handler mHandler;

    boolean isRec = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "======================  onCreate  ======================");
        Log.i(TAG, "========================================================");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        surfaceHolder = videoView.getHolder();
        btnStart = findViewById(R.id.btnS);
        btnEnd = findViewById(R.id.btnE);
        context = this;
        Util.checkPermission(context, 100);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera();
            }
        });

        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camaraWrapper != null)
                    camaraWrapper.close();
                camaraWrapper = null;
                btnStart.setText("녹화시작");
            }
        });

        camaraWrapper = new CamaraWrapper();
        camera = camaraWrapper.open(0, ImageFormat.NV21, surfaceHolder);
        size = camaraWrapper.getSize();

//        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        surfaceHolder.addCallback(this);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "=======================  onPause  ======================");
        Log.i(TAG, "========================================================");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "=======================  onResume  =====================");
        Log.i(TAG, "========================================================");
        super.onResume();
    }

    private void startCamera() {
        isRec = true;
        camaraWrapper.build();
    }

//    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "===================  surfaceCreated  ===================");
        Log.i(TAG, "========================================================");
        try {
            camera.unlock();
            camera.reconnect();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "===================  surfaceChanged  ===================");
        Log.i(TAG, "========================================================");
        if (camera != null && isRec) {
            size.width = width;
            size.height = height;
            Camera.Parameters parameters = camera.getParameters();
            callbackBuffer = null;
            callbackBuffer = new byte[parameters.getPreviewSize().width *
                    parameters.getPreviewSize().height *
                    ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
            camera.addCallbackBuffer(callbackBuffer);
            camera.setPreviewCallbackWithBuffer(camaraWrapper.getPreviewCallback());
        }
    }

//    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "========================================================");
        Log.i(TAG, "===================  surfaceDestroyed  ==================");
        Log.i(TAG, "========================================================");

    }

    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
//            camaraWrapper = new CamaraWrapper(context);
//            camaraWrapper.open(0, ImageFormat.NV21, videoView.getHolder());
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            btnStart.setText("녹화중");

        }
    }

}
