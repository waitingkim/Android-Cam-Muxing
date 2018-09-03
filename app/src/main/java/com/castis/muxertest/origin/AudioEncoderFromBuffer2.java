package com.castis.muxertest.origin;

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

public class AudioEncoderFromBuffer2 {

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

    private int bufferSize;
    private static final int TIMEOUT_USEC = 10000;

    private EncoderCallback encoderCallback;

    private boolean isClose = false;

    public AudioEncoderFromBuffer2(long mStartTime) {

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

        /*MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, asample_rate, achannel);
        aformat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        aformat.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * ABITRATE_KBPS);
        aformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);*/

        MediaFormat aformat = new MediaFormat();
        aformat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        aformat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        aformat.setInteger("max-bitrate", 24000);
        aformat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        aformat.setInteger(MediaFormat.KEY_BIT_RATE, 24000);

        Log.i(TAG, "start aac aencoder");


        ByteBuffer bb = ByteBuffer.allocateDirect(2);
        bb.put(new byte[]{(byte) 0x12, (byte) 0x88});
        bb.flip();
        aformat.setByteBuffer("csd-0", bb);

        Log.d(TAG, "Audio encoder output origin format changed: " + aformat);

        aencoder.configure(aformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        isClose = false;

    }

    public MediaCodec getAudioCodec() {
        return aencoder;
    }

    public void start() {
        Log.i(TAG, "start aac aencoder");
//        atrack = encoderCallback.addTrack(aencoder.getOutputFormat());
//        Log.i(TAG, "start aac aencoder atrack : " + atrack);

        Log.i(TAG, String.format("Audio start the mic in rate=%dHZ, channels=%d, format=%d", asample_rate, achannel, abits));
        mic.startRecording();

        aencoder.start();

        // start audio worker thread.
        aworker = new Thread(new Runnable() {
            @Override
            public void run() {
                fetchAudioFromDevice();
            }
        });
        Log.i(TAG, "Audio start audio worker thread.");
        aloop = true;
        aworker.start();
    }

    public void close() {
        Log.i(TAG, "Audio close");
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

        handleEndOfStream();

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

        if (encoderCallback != null) {
            encoderCallback.stop();
            encoderCallback.release();
            encoderCallback = null;
        }

    }

    private void handleEndOfStream() {
        Log.d(TAG, "Audio handleEndOfStream");
        int inputBufferIndex = aencoder.dequeueInputBuffer(TIMEOUT_USEC);
        aencoder.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_PARTIAL_FRAME);

        ByteBuffer[] outputBuffers = aencoder.getOutputBuffers();
        int outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 0);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE)
                    Log.d(TAG, "Audio no output from encoder available");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder

                MediaFormat newFormat = aencoder.getOutputFormat();
                Log.d(TAG, "Audio encoder output format changed: " + newFormat);

                atrack = encoderCallback.addTrack(newFormat);
                encoderCallback.start();
            } else {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                onEncodedAacFrame(outputBuffer, bufferInfo);
                Log.e(TAG, outputBuffer.capacity() + " bytes written");

                aencoder.releaseOutputBuffer(outputBufferIndex, false);

                outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                Log.i(TAG, "Audio =============While outputBufferIndex-->" + outputBufferIndex);
                Log.e("AudioEncoder", outputBuffer.capacity() + " bytes written");

            }

        } while (outputBufferIndex >= 0);
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

    private synchronized void fetchAudioFromDevice() {

        ByteBuffer[] inputBuffers = aencoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aencoder.getOutputBuffers();
        int inputBufferIndex = 0, len = 0;

        while (aloop && mic != null && !Thread.interrupted()) {
            inputBufferIndex = aencoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                inputBuffers[inputBufferIndex].clear();
                len = mic.read(inputBuffers[inputBufferIndex], bufferSize);
                if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                    Log.d(TAG, "Audio len " + "AudioRecord.ERROR_INVALID_OPERATION ");
                } else {
                    aencoder.queueInputBuffer(inputBufferIndex, 0, len, System.nanoTime() / 1000, 0);
                }
            }

            int outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 0);
            Log.i(TAG, "============= Audio outputBufferIndex-->" + outputBufferIndex);

            do {
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE)
                        Log.d(TAG, "Audio no output from encoder available");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder

                    MediaFormat newFormat = aencoder.getOutputFormat();
                    Log.d(TAG, "Audio encoder output format changed: " + newFormat);

                    atrack = encoderCallback.addTrack(newFormat);
                    Log.d(TAG, "Audio encoder output format changed atrack : " + atrack);
                    encoderCallback.start();
                } else {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                    onEncodedAacFrame(outputBuffer, bufferInfo);
                    Log.e(TAG, outputBuffer.capacity() + " bytes written");

                    aencoder.releaseOutputBuffer(outputBufferIndex, false);

                    outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 10000);
                    Log.i(TAG, "Audio =============While outputBufferIndex-->" + outputBufferIndex);
                    Log.e("AudioEncoder", outputBuffer.capacity() + " bytes written");

                    /*int outBitsSize = bufferInfo.size;
                    int outPacketSize = outBitsSize + 7;    // 7 is ADTS size
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + outBitsSize);

                    byte[] outData = new byte[outPacketSize];
                    addADTStoPacket(outData, outPacketSize);

                    outputBuffer.get(outData, 7, outBitsSize);
                    outputBuffer.position(bufferInfo.offset);

                    onEncodedAacFrame(outputBuffer, bufferInfo);

                    Log.e("AudioEncoder", outData.length + " bytes written");

                    aencoder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = aencoder.dequeueOutputBuffer(bufferInfo, 0);*/

                }

            } while (outputBufferIndex >= 0);
        }



    }

    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private void addADTStoPacket (byte[] packet, int packetLen){
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

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
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        try {
//            Log.d(TAG, "====AAC writeSampleData atrack : " + atrack + " / es : " + es.capacity() + " / " + es.limit() + " / " + es.position());
//            Log.d(TAG, "====AAC writeSampleData bi : " + bi.size + " / " + bi.offset + " / " + bi.presentationTimeUs + " / " + bi.flags);

            es.position(bi.offset);
            es.limit(bi.offset + bi.size);

            encoderCallback.writeSampleData(atrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write audio sample failed.");
            e.printStackTrace();
        }
    }

    public void setMuxerCallback(EncoderCallback encoderCallback) {
        this.encoderCallback = encoderCallback;
    }
}



