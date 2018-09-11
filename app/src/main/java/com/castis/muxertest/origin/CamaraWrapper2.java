package com.castis.muxertest.origin;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.AsyncTask;
import android.util.Log;
import android.view.SurfaceHolder;

import com.castis.muxertest.Logger;
import com.castis.muxertest.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CamaraWrapper2 {

    private final String TAG = "VideoEncoderFromBuffer";
    public final static int VWIDTH = 640;
    public final static int VHEIGHT = 480;

    public int frameCnt = 0;

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
        parameters.set("orientation", "landscape"); //landscape
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

        File file = Util.getOutputMediaFile("mp4", 0, ".mp4");
        Logger.i(TAG, "Output FileName : " + file.getName());
        try {
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        cameraPreviewCallback = new CameraPreviewCallback();
        encoderCallback = new EncoderCallback() {
            int cnt = 0, stopCnt = 0, release = 0;
            int frameMaxCnt = 60;
            byte[] fileFrame = new byte[0];
            ArrayList<byte[]> frameBytes = new ArrayList<>();
            String[] fileInfos = null;


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
            public synchronized void writeSampleData(String type, int trackIndex, ByteBuffer writeByteBuffer, MediaCodec.BufferInfo bufferInfo) {
                if (trackIndex == 0)
                    Logger.w(CamaraWrapper2.class, "count : " + cnt + " / writeSampleData track : " + trackIndex +
                            " / Buffer[ pos=" + writeByteBuffer.position() + " lim=" + writeByteBuffer.limit() + " cap=" + writeByteBuffer.capacity() + " ]" +
                            " / Info[ offset=" + bufferInfo.offset + " size=" + bufferInfo.size + " pts=" + bufferInfo.presentationTimeUs + " ]");
                else
                    Logger.e(CamaraWrapper2.class, "count : " + cnt + " / writeSampleData track : " + trackIndex +
                            " / Buffer[ pos=" + writeByteBuffer.position() + " lim=" + writeByteBuffer.limit() + " cap=" + writeByteBuffer.capacity() + " ]" +
                            " / Info[ offset=" + bufferInfo.offset + " size=" + bufferInfo.size + " pts=" + bufferInfo.presentationTimeUs + " ]");


                if (cnt > 1) {
                    muxer.writeSampleData(trackIndex, writeByteBuffer, bufferInfo);
                }


                int size = 1 + 8 + 8 + 4 + writeByteBuffer.limit();
                Logger.w(CamaraWrapper2.class, "size : " + size);

                byte[] frame = new byte[size];
                byte[] data = new byte[writeByteBuffer.limit()];


                writeByteBuffer.get(data, 0, data.length);

                ArrayList<byte[]> bytes = new ArrayList<>();
                ArrayList<String[]> ptsList = new ArrayList<>();

                bytes.add(0, Util.framePacketizing(trackIndex, 1));
                bytes.add(1, Util.framePacketizing(bufferInfo.presentationTimeUs, 8));
                bytes.add(2, Util.framePacketizing(bufferInfo.presentationTimeUs, 8));
                bytes.add(3, Util.framePacketizing(bufferInfo.size, 4));
                bytes.add(4, data);

                System.arraycopy(bytes.get(0), 0, frame, 0, bytes.get(0).length);
                System.arraycopy(bytes.get(1), 0, frame, bytes.get(0).length, bytes.get(1).length);
                System.arraycopy(bytes.get(2), 0, frame, (bytes.get(0).length + bytes.get(1).length), bytes.get(2).length);
                System.arraycopy(bytes.get(3), 0, frame, (bytes.get(0).length + bytes.get(1).length + bytes.get(2).length), bytes.get(3).length);
                System.arraycopy(bytes.get(4), 0, frame, (bytes.get(0).length + bytes.get(1).length + bytes.get(2).length + bytes.get(3).length), bytes.get(4).length);



                frameBytes.add(frame);

                if (fileInfos == null)
                    fileInfos = new String[]{type, String.valueOf(trackIndex), String.valueOf(bufferInfo.presentationTimeUs)};

                if (frameCnt > frameMaxCnt) {
                    String[] fileInfo = fileInfos;
                    Logger.e("###", Arrays.toString(fileInfo));
                    File file = Util.getOutputMediaFile("h264", Long.parseLong(fileInfo[2]), "_" + fileInfo[0] + "_" + fileInfo[1] + ".h264");
//                    try {

                    // frameBytes 바이트배열로 묶는 처리 후 파일로 생성
                    int totalSize = 0;
                    for (int i = 0; i < frameBytes.size(); i++) {
                        totalSize = totalSize + frameBytes.get(i).length;
//                                Logger.d("##########", "frameBytes.size() : " + frameBytes.size() + " / totalsize : " + totalSize);
                    }
                    byte[] aaa = new byte[totalSize];

                    int destPos = 0;
                    for (int i = 0; i < frameBytes.size(); i++) {
//                                Logger.d("##########", "destPos : " + destPos);
                        System.arraycopy(frameBytes.get(i), 0, aaa, destPos, frameBytes.get(i).length);
                        destPos = destPos + frameBytes.get(i).length;
                    }

//                        FileOutputStream stream = new FileOutputStream(file.getAbsoluteFile());
//                        stream.write(aaa);
//                        stream.close();

                    MyAsyncTask myAsyncTask = new MyAsyncTask();
                    myAsyncTask.setFrame(frame);
//                    Integer integer = Integer.valueOf(frameCnt);
//                myAsyncTask.onPostExecute(integer);
                    myAsyncTask.execute();

                    frameBytes.clear();
                    ptsList.clear();
                    fileInfos = null;
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    frameCnt = 0;
                } else {
                    frameCnt++;
                }


            }
        };

        fromBuffer2 = new VideoEncoderFromBuffer2(vSize, mStartTime);
        fromBuffer2.setMuxerCallback(encoderCallback);

        afromBuffer2 = new AudioEncoderFromBuffer2(mStartTime);
        afromBuffer2.setMuxerCallback(encoderCallback);

        Log.i(TAG, "oenEnd");
        return camera;
    }


    public class MyAsyncTask extends AsyncTask<Void, Integer, Integer> {
        byte[] frame;

        public MyAsyncTask() {
        }

        public void setFrame(byte[] frame) {
            this.frame = frame;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            Logger.i("CamaraWrapper2.class", "doInBackground");
            try {
                URL url = new URL("http://192.168.11.8:3001/ktBrokering/version/" + frameCnt);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.addRequestProperty("Connection", "keep-alive");
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setUseCaches(false);
//                        conn.connect();

                OutputStream os = conn.getOutputStream();
                os.write(frame);
                os.flush();
                os.close();

                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println("line:" + line);
                }

                int responseCode = conn.getResponseCode();
                //System.out.println("responseCode : " + responseCode);
                Logger.i("CamaraWrapper2.class", "responseCode : " + responseCode);
//                        conn.disconnect();

                        /*OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                        writer.write("{msg:success}");
                        writer.close();
                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                        } else {

                        }

*/
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            frameCnt++;
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            Logger.i("CamaraWrapper2.class", "onPostExecute integer : " + integer.intValue());
        }

    }


    class BufferToHttp implements Runnable {

        byte[] frame;

        public BufferToHttp(byte[] frame) {
            this.frame = frame;
        }

        @Override
        public void run() {
            try {
                URL url = new URL("http://192.168.11.8:3001/ktBrokering/version/1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.addRequestProperty("Connection", "keep-alive");
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setUseCaches(false);
//                        conn.connect();

                OutputStream os = conn.getOutputStream();
                os.write(frame);
                os.flush();
                os.close();

                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println("line:" + line);
                }

                int responseCode = conn.getResponseCode();
                //System.out.println("responseCode : " + responseCode);
                Logger.i("CamaraWrapper2.class", "responseCode : " + responseCode);
//                        conn.disconnect();

                        /*OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                        writer.write("{msg:success}");
                        writer.close();
                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                        } else {

                        }
*/
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
//            Log.i(TAG, "onPreviewFrame " + data.toString());
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

    public class SendFrames extends Thread {

        public SendFrames() {

        }

        public void run() {
            while (true) {
                try {
                    Thread.sleep(100);
                    Logger.i("CamaraWrapper2.class", "doInBackground");

                    URL url = new URL("http://192.168.11.8:3001/ktBrokering/version/" + frameCnt);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5 * 1000);
                    conn.addRequestProperty("Connection", "keep-alive");
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setUseCaches(false);

                    OutputStream os = conn.getOutputStream();
                    os.write( null );
                    os.flush();
                    os.close();

                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        System.out.println("line:" + line);
                    }

                    int responseCode = conn.getResponseCode();
                    //System.out.println("responseCode : " + responseCode);
                    Logger.i("CamaraWrapper2.class", "responseCode : " + responseCode);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                frameCnt++;

            }
        }

    }
}
