package com.castis.muxertest.origin;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import com.castis.muxertest.Logger;
import com.castis.muxertest.MainActivity;
import com.castis.muxertest.NV21Convertor;
import com.castis.muxertest.Util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoEncoderFromBuffer2 {

    private static final String TAG = "VideoEncoderFromBuffer";
    private static final boolean VERBOSE = true; // lots of logging

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 30; // 15fps
    private static final int IFRAME_INTERVAL = FRAME_RATE; // 10 between

    // I-frames
    private int TIMEOUT_USEC = 10000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = MainActivity.VHEIGHT * MainActivity.VWIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    private MediaCodec mMediaCodec;
//    private MediaMuxer mMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex = -1;
    private boolean mMuxerStarted;
    byte[] mFrameData;
    private int mColorFormat;
    private long mStartTime = 0;

    private int vcolor;
    private MediaCodecInfo vmci;
    private static final String VCODEC = "video/avc";
    NV21Convertor mNV21 = null;

    private EncoderCallback encoderCallback;

    private int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select one when type matched.
        //      2. perfer google avc.
        //      3. perfer qcom avc.
        vmci = chooseVideoEncoder(null, null);
        //vmci = chooseVideoEncoder("google", vmci);
        //vmci = chooseVideoEncoder("qcom", vmci);

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if ((cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar)) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("vencoder %s support profile %d, level %d", vmci.getName(), pl.profile, pl.level));
        }

        Log.i(TAG, String.format("vencoder %s choose color format 0x%x(%d)", vmci.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }

    private MediaCodecInfo chooseVideoEncoder(String name, MediaCodecInfo def) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(VCODEC)) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return def;
    }

    public File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "h264");

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMddmmss", Locale.US).format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + this.vsize.width + "x" + this.vsize.height + timeStamp + ".mp4");

        return mediaFile;
    }

    android.hardware.Camera.Size vsize;

    private int selectColorFormat(String mimeType) {
        MediaCodecInfo codecInfo = selectCodec(mimeType);
        int colorFormat = 0;
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {

            if (isRecognizedFormat(capabilities.colorFormats[i])) {
                colorFormat = capabilities.colorFormats[i];
                break;
            }
        }
        return colorFormat;
    }

    @SuppressLint("NewApi")
    public VideoEncoderFromBuffer2(android.hardware.Camera.Size size, long mStartTime) {
        Logger.d(this, "VideoEncoder startTime : " + mStartTime);
        this.vsize = size;

        mFrameData = new byte[this.vsize.width * this.vsize.height * 3 / 2];

        vcolor = chooseVideoEncoder();

        mBufferInfo = new MediaCodec.BufferInfo();
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (VERBOSE)
            Log.d(TAG, "found codec: " + codecInfo.getName());
        mColorFormat = selectColorFormat(MIME_TYPE);

        if (VERBOSE)
            Log.d(TAG, "found colorFormat: " + mColorFormat + " / vcolor : " + vcolor);

//        mColorFormat = vcolor;
        mColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, this.vsize.width, this.vsize.height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

        try {
            mMediaCodec = MediaCodec.createByCodecName(codecInfo.getName());


        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        File file = getOutputMediaFile();
        Log.i(TAG, "videofile: " + file.getName());
        this.mStartTime = mStartTime;
        /** mStartTime = System.nanoTime();*/

        /*try {
            mMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }*/
        mTrackIndex = -1;
        mMuxerStarted = false;

        mNV21 = new NV21Convertor();

        mNV21.setSize(this.vsize.width, this.vsize.height);
        mNV21.setSliceHeigth(this.vsize.height);
        mNV21.setStride(this.vsize.width);
        mNV21.setYPadding(0);
        mNV21.setEncoderColorFormat(mColorFormat);
    }

    public void bitmapToFile(byte[] input, android.hardware.Camera.Parameters parameters) {
        Log.i(TAG, "encodeFrame()");
        long encodedSize = 0;

        if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            YV12toYUV420Planar(input, mFrameData, vsize.width, vsize.height);
        } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
            YV12toYUV420PackedSemiPlanar(input, mFrameData, vsize.width, vsize.height);
        } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            YV12toYUV420PackedSemiPlanar(input, mFrameData, vsize.width, vsize.height);
        } else {
            System.arraycopy(input, 0, mFrameData, 0, mFrameData.length);
        }

        Bitmap bm = Util.decodeNV21(input, parameters);
        File file = Util.getOutputMediaFile("bitmpa", 0, "bitmap2.jpg");
        Util.SaveBitmapToFileCache(bm, file.getAbsolutePath());
    }

    public synchronized void encodeFrame(byte[] input/* , byte[] output */) {
//        Log.i(TAG, "encodeFrame() " + mFrameData.toString() + " / " + mFrameData.length);
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (VERBOSE)
            Log.i(TAG, "inputBufferIndex-->" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            long endTime = System.nanoTime();
            long ptsUsec = (endTime - mStartTime) / 1000;
//            Logger.i("VideoEncoder", "presentationTime: " + ptsUsec);
            mNV21.convert(input, inputBuffers[inputBufferIndex]);
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.capacity(), ptsUsec, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE)
                Log.d(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE)
                    Log.d(TAG, "no output from encoder available");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                outputBuffers = mMediaCodec.getOutputBuffers();
                if (VERBOSE)
                    Log.d(TAG, "encoder output buffers changed");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder

                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                /*mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();*/
                mTrackIndex = encoderCallback.addTrack("Video", newFormat);
                Log.d(TAG, "encoder output format changed mTrackIndex : " + mTrackIndex);
                encoderCallback.start();
                mMuxerStarted = true;
            } else if (outputBufferIndex < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        outputBufferIndex);
                // let's ignore it
            } else {
                if (VERBOSE)
                    Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
//						throw new RuntimeException("muxer hasn't started");
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        mTrackIndex = encoderCallback.addTrack("Video", newFormat);
                        Log.d(TAG, "encoder output format changed mTrackIndex : " + mTrackIndex);
                        encoderCallback.start();
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    encoderCallback.writeSampleData("V", mTrackIndex, outputBuffer, mBufferInfo);
                    if (VERBOSE) {
//                        Log.d(TAG, "sent " + outputBuffer.capacity() + " bytes to muxer");
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }

    @SuppressLint("NewApi")
    public void close() {
        // try {
        // mFileOutputStream.close();
        // } catch (IOException e) {
        // System.out.println(e);
        // } catch (Exception e) {
        // System.out.println(e);
        // }
        Log.i(TAG, "close()");

        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (encoderCallback != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            // of frames submitted, and don't call stop() if we haven't written anything.
            encoderCallback.stop();
            encoderCallback.release();
            encoderCallback = null;
        }
    }

    private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

    private static byte[] YV12toYUV420PackedSemiPlanar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    /**
     * Returns true if this is a color format that this test code understands
     * (i.e. we know how to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    public void setMuxerCallback(EncoderCallback encoderCallback) {
        this.encoderCallback = encoderCallback;
    }

    private void handleEndOfStream() {
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_PARTIAL_FRAME);

        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE)
                    Log.d(TAG, "no output from encoder available");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                outputBuffers = mMediaCodec.getOutputBuffers();
                if (VERBOSE)
                    Log.d(TAG, "encoder output buffers changed");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder

                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                /*mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();*/
                mTrackIndex = encoderCallback.addTrack("Video", newFormat);
                Log.d(TAG, "encoder output format changed mTrackIndex : " + mTrackIndex);
                encoderCallback.start();
                mMuxerStarted = true;
            } else if (outputBufferIndex < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        outputBufferIndex);
                // let's ignore it
            } else {
                if (VERBOSE)
                    Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
//						throw new RuntimeException("muxer hasn't started");
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        mTrackIndex = encoderCallback.addTrack("Video", newFormat);
                        Log.d(TAG, "encoder output format changed mTrackIndex : " + mTrackIndex);
                        encoderCallback.start();
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    encoderCallback.writeSampleData("V", mTrackIndex, outputBuffer, mBufferInfo);
                    if (VERBOSE) {
                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }
}
