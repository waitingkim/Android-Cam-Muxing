package com.castis.muxertest;

import android.app.Activity;
import android.content.Context;
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
import android.widget.VideoView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "///////////////////onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        surfaceHolder = videoView.getHolder();
        btnStart = findViewById(R.id.btnS);
        btnEnd = findViewById(R.id.btnE);
        context = this;

        mHandler = new Handler();

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() { // UI 작업 수행 X
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // UI 작업 수행 O
                        startCamera();
                    }
                });
            }
        });
//        t.start();



        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            t.start();
//                ((MainActivity)context).runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            startCamera();
//                        }
//                        catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });

//                HandlerThread t = new HandlerThread("My Handler Thread");
//                t.start();
//                mHandler = new Handler(t.getLooper());


                startCamera();

//                camaraWrapper = new CamaraWrapper(context);
//                camaraWrapper.open(0, ImageFormat.NV21, videoView.getHolder());
//                mediaPrepareTask.execute();
            }
        });

        camaraWrapper = new CamaraWrapper(this);
        camera = camaraWrapper.open(0, ImageFormat.NV21, surfaceHolder);
        size = camaraWrapper.getSize();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);

        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camaraWrapper != null)
                    camaraWrapper.close();
                camaraWrapper = null;
                btnStart.setText("녹화시작");
            }
        });

//        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

//        camaraWrapper = new CamaraWrapper(this);
//        camaraWrapper.open(0, ImageFormat.NV21, videoView.getHolder());
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "///////////////////onPause()");
        super.onPause();
//        camaraWrapper.close();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "///////////////////onResume()");
        super.onResume();

//        camaraWrapper = new CamaraWrapper(this);
//        camaraWrapper.open(0, ImageFormat.NV21, surfaceHolder);
    }

    private void startCamera() {
//        camaraWrapper = new CamaraWrapper(this);
//        camaraWrapper.open(0, ImageFormat.NV21, videoView.getHolder());

//        videoView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        videoView.getHolder().addCallback(camaraWrapper);


        camera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Main surfaceCreated");
        try {
            camera.unlock();
            camera.reconnect();
            camera.setPreviewDisplay(holder);
//            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "Main surfaceChanged");
        if (camera != null) {
            size.width = width;
            size.height = height;
            Camera.Parameters parameters = camera.getParameters();
            callbackBuffer = null;
            callbackBuffer = new byte[parameters.getPreviewSize().width *
                    parameters.getPreviewSize().height *
                    ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
            camera.addCallbackBuffer(callbackBuffer);
            camera.setPreviewCallbackWithBuffer(camaraWrapper.getPreviewCallback());
//            camera.setPreviewCallback(cameraPreviewCallback);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            camaraWrapper = new CamaraWrapper(context);
            camaraWrapper.open(0, ImageFormat.NV21, videoView.getHolder());
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
