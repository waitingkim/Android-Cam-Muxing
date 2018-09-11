package com.castis.muxertest.origin;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.castis.muxertest.Logger;
import com.castis.muxertest.R;
import com.castis.muxertest.Util;

import java.io.File;
import java.io.IOException;

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

    /**
     * Permission check.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public void checkPermission(Context context, int requestCode) {
        Logger.e("TAG", "CheckPermission : " + context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (this.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(context, "Read/Write external storage", Toast.LENGTH_SHORT).show();
            }

            this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET},
                    requestCode);
        } else {
            Logger.e("TAG", "permission deny");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 100:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {

                    writeFile();

                    // permission was granted, yay! do the
                    // calendar task you need to do.

                } else {

                    Logger.d("TAG", "Permission always deny");

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
        }
    }

    /**
     * Create file example.
     */
    private void writeFile() {
        File file = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "temp.txt");
        try {
            Logger.d("TAG", "create new File : " + file.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "///////////////////onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        checkPermission(this, 100);
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

        videoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Logger.d("AUTOFOCUS", "success : " + success);
                    }
                });
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
