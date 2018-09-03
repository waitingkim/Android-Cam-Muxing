package com.castis.muxertest;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoderFromBuffer {

    private static final String TAG = "VideoEncoderFromBuffer";
    private static final boolean VERBOSE = true; // lots of logging
    private static final String ACODEC = "audio/mp4a-latm";

    // audio device.
    private AudioRecord mic;
    private byte[] abuffer;
    private MediaCodec aencoder;
    private MediaCodec.BufferInfo aebi;

    // use worker thread to get audio packet.
    private Thread aworker;
    private boolean aloop;

    // audio mic settings.
    private int asample_rate;
    private int achannel;
    private int abits;
    private int atrack;
    private static final int ABITRATE_KBPS = 24;
    private long mStartTime = 0;
    private MediaMuxer muxer;

    private int bufferSize;

    public AudioEncoderFromBuffer(long mStartTime) {


        this.mStartTime = mStartTime;
        // open mic, to find the work one.
        if ((mic = chooseAudioDevice()) == null) {
            Log.e(TAG, String.format("mic find device mode failed."));
            return;
        }

        // aencoder yuv to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aencoder = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return;
        }
        aebi = new MediaCodec.BufferInfo();


        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, asample_rate, achannel);
//        MediaFormat aformat = new MediaFormat();
//        aformat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
//        aformat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//        aformat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 8000);
//        aformat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//        aformat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        aformat.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * ABITRATE_KBPS);
        aformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);



        /*ByteBuffer bb = ByteBuffer.allocateDirect(2);
        bb.put(new byte[]{(byte) 0x12, (byte) 0x88});
        bb.flip();
        aformat.setByteBuffer("csd-0", bb);*/

        aencoder.configure(aformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    public MediaCodec getAudioCodec() {
        return aencoder;
    }

    public void start() {
        Log.i(TAG, "start aac aencoder");

        Log.i(TAG, String.format("start the mic in rate=%dHZ, channels=%d, format=%d", asample_rate, achannel, abits));
        mic.startRecording();

        aencoder.start();

        // start audio worker thread.
        aworker = new Thread(new Runnable() {
            @Override
            public void run() {
                fetchAudioFromDevice();
            }
        });
        Log.i(TAG, "start audio worker thread.");
        aloop = true;
        aworker.start();
    }

    public void close() {
        aloop = false;
        if (aworker != null) {
            aworker.interrupt();
            try {
                aworker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            aworker = null;
        }

        if (mic != null) {
            Log.i(TAG, "stop mic");
            mic.setRecordPositionUpdateListener(null);

            mic.stop();
            mic.release();
            mic = null;
        }
        if (aencoder != null) {
            Log.i(TAG, "stop aencoder");
            aencoder.stop();
            aencoder.release();
            aencoder = null;
        }
    }

    public AudioRecord chooseAudioDevice() {
        int[] sampleRates = {44100, 22050, 11025};
        for (int sampleRate : sampleRates) {
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;

            int bSamples = 8;
            if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                bSamples = 16;
            }

            int nChannels = 2;
            if (channelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO) {
                nChannels = 1;
            }

            //int bufferSize = 2 * bSamples * nChannels / 8;
            bufferSize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "initialize the mic failed.");
                continue;
            }

            asample_rate = sampleRate;
            abits = audioFormat;
            achannel = nChannels;
            mic = audioRecorder;
            abuffer = new byte[Math.min(4096, bufferSize)];
            //abuffer = new byte[bufferSize];
            Log.i(TAG, String.format("mic open rate=%dHZ, channels=%d, bits=%d, buffer=%d/%d, state=%d",
                    sampleRate, nChannels, bSamples, bufferSize, abuffer.length, audioRecorder.getState()));
            break;
        }

        return mic;
    }

    private void fetchAudioFromDevice() {

        ByteBuffer[] inputBuffers = aencoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aencoder.getOutputBuffers();
        int inputBufferIndex =0, len = 0;

        while (aloop && mic != null && !Thread.interrupted()) {
            inputBufferIndex = aencoder.dequeueInputBuffer(10000);
            if(inputBufferIndex >= 0){
                inputBuffers[inputBufferIndex].clear();
                len = mic.read(inputBuffers[inputBufferIndex], bufferSize);
                if(len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE){
                    Log.d(TAG, "len " + "AudioRecord.ERROR_INVALID_OPERATION ");
                } else {
                    aencoder.queueInputBuffer(inputBufferIndex, 0, len, System.nanoTime() / 1000, 0);
                }
            }
//            int size = mic.read(abuffer, 0, abuffer.length);
//            if (size <= 0) {
//                Log.i(TAG, "audio ignore, no data to read.");
//                break;
//            }
//            byte[] audio = new byte[size];
//            System.arraycopy(abuffer, 0, audio, 0, size);

//            onGetPcmFrame(audio);

            int outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 10000);
            Log.i(TAG, "============= outputBufferIndex-->" + outputBufferIndex);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                onEncodedAacFrame(outputBuffer, bufferInfo);
                Log.e("AudioEncoder", outputBuffer.capacity() + " bytes written");

                aencoder.releaseOutputBuffer(outputBufferIndex, false);

                outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 10000);
                Log.i(TAG, "=============While outputBufferIndex-->" + outputBufferIndex);
            }


        }

    }

    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private static final int TIMEOUT_USEC = 10000;

    private void onGetPcmFrame(byte[] data) {
        Log.i(TAG, String.format("got PCM audio, size=%d", data.length));

        try {
            ByteBuffer[] inputBuffers = aencoder.getInputBuffers();
            ByteBuffer[] outputBuffers = aencoder.getOutputBuffers();
            int inputBufferIndex = aencoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(data);
                aencoder.queueInputBuffer(inputBufferIndex, 0, inputBuffer.capacity(), System.nanoTime() / 1000, 0);
            }

            int outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 0);
            Log.i(TAG, "============= outputBufferIndex-->" + outputBufferIndex);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                onEncodedAacFrame(outputBuffer, bufferInfo);
                Log.e("AudioEncoder", outputBuffer.capacity() + " bytes written");

                aencoder.releaseOutputBuffer(outputBufferIndex, false);

                outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 0);
                Log.i(TAG, "=============While outputBufferIndex-->" + outputBufferIndex);
            }

            // feed the aencoder with yuv frame, got the encoded 264 es stream.
            /*ByteBuffer[] inBuffers = aencoder.getInputBuffers();
            ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

            if (true) {
                int inBufferIndex = aencoder.dequeueInputBuffer(-1);
                Log.i(TAG, String.format("onGetPcmFrame try to dequeue input vbuffer, ii=%d", inBufferIndex));
                if (inBufferIndex >= 0) {
                    ByteBuffer bb = inBuffers[inBufferIndex];
                    bb.clear();
                    bb.put(data, 0, data.length);
                    long endTime = System.nanoTime();
                    long ptsUsec = (endTime - mStartTime) / 1000;

//                long pts = new Date().getTime() * 1000 - presentationTimeUs;
                    Log.i(TAG, String.format("feed PCM to encode %dB, pts=%d", data.length, ptsUsec / 1000));
                    //SrsHttpFlv.srs_print_bytes(TAG, data, data.length);
                    aencoder.queueInputBuffer(inBufferIndex, 0, data.length, ptsUsec, 0);
                }
            }

            for (; ; ) {
                int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
                Log.i(TAG, String.format("onGetPcmFrame try to dequeue output vbuffer, oi=%d", outBufferIndex));
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    onEncodedAacFrame(bb, aebi);
                    aencoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }*/
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        try {
            Log.d(TAG, "====AAC writeSampleData atrack : " + atrack + " / es : " + es.capacity() + " / " + es.limit() + " / " + es.position());
            Log.d(TAG, "====AAC writeSampleData bi : " + bi.size + " / " + bi.offset + " / " + bi.presentationTimeUs + " / " + bi.flags);

            es.position(bi.offset);
            es.limit(bi.offset + bi.size);

            muxer.writeSampleData(atrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write audio sample failed.");
            e.printStackTrace();
        }
    }

    public void setMuxer(MediaMuxer mMuxer) {
        this.muxer = mMuxer;
    }

    public void setTrackIndex(int trackIndex) {
        atrack = trackIndex;
    }
}



