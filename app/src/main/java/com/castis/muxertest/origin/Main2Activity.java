package com.castis.muxertest.origin;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.castis.muxertest.R;

public class Main2Activity extends Activity implements SurfaceHolder.Callback{

    // video device.
    public final static int VWIDTH = 640;
    public final static int VHEIGHT = 480;
    private Camera.Size vsize;

    String TAG = "VideoEncoderFromBuffer";

    SurfaceView videoView;

    Button btnStart, btnEnd, btnRecStart, btnRecEnd;

    TextView textView;

    CamaraWrapper2 camaraWrapper;

    Context context;

    SurfaceHolder surfaceHolder;

    Camera camera = null;

    Camera.Size size = null;

    private byte[] callbackBuffer;

    Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "///////////////////onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        videoView = findViewById(R.id.videoView);
        surfaceHolder = videoView.getHolder();
        btnStart = findViewById(R.id.btnS);
        btnEnd = findViewById(R.id.btnE);
        btnRecStart = findViewById(R.id.btnRecS);
        btnRecEnd = findViewById(R.id.btnRecE);
        textView = findViewById(R.id.text);
        context = this;

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camaraWrapper.afromBuffer2.start();
                startCamera();
                textView.setText("녹화중");
            }
        });

        camaraWrapper = new CamaraWrapper2();
        camera = camaraWrapper.open(0, ImageFormat.NV21, surfaceHolder);
        size = camaraWrapper.getSize();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);

        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camaraWrapper.afromBuffer2.close();
                if (camaraWrapper != null)
                    camaraWrapper.close();
                camera.addCallbackBuffer(null);
                camera.setPreviewCallbackWithBuffer(null);
                camaraWrapper = null;
                textView.setText("녹화끝");
            }
        });


        btnRecStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("녹음중");
                camaraWrapper.afromBuffer2.start();
            }
        });


        btnRecEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("녹음끝");
                camaraWrapper.afromBuffer2.close();
                camaraWrapper.fromBuffer2.close();
            }
        });

//        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "///////////////////onPause()");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "///////////////////onResume()");
        super.onResume();
    }

    private void startCamera() {
        Camera.Parameters parameters = camera.getParameters();
        callbackBuffer = null;
        callbackBuffer = new byte[parameters.getPreviewSize().width *
                parameters.getPreviewSize().height *
                ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
        camera.addCallbackBuffer(callbackBuffer);
        camera.setPreviewCallbackWithBuffer(camaraWrapper.getPreviewCallback());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Main surfaceCreated");
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
        Log.i(TAG, "Main surfaceChanged");
        if (camera != null) {
            size.width = width;
            size.height = height;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }


}
