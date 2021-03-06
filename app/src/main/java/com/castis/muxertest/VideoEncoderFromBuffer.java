package com.castis.muxertest;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class VideoEncoderFromBuffer {

    private static final String TAG = "VideoEncoderFromBuffer";
    private static final boolean VERBOSE = true; // lots of logging
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/Movies/h264";
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 30; // 15fps
    private static final int IFRAME_INTERVAL = FRAME_RATE; // 10 between
    // I-frames
    private static final int TIMEOUT_USEC = 10000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = MainActivity.VHEIGHT * MainActivity.VWIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    private int mWidth;
    private int mHeight;
    private MediaCodec aMediaCodec;
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex = -1;
    private int mATrackIndex = -1;
    private boolean mMuxerStarted;
    byte[] mFrameData;
    FileOutputStream mFileOutputStream = null;
    private int mColorFormat;
    private long mStartTime = 0;

    private int vcolor;
    private MediaCodecInfo vmci;
    private static final String VCODEC = "video/avc";
    private int trackIndex;
    private Encoder encoderCallback;

    android.hardware.Camera.Size vsize;
    NV21Convertor mNV21 = null;

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
    public VideoEncoderFromBuffer(long startTime, Encoder callback, android.hardware.Camera.Size size) {
        Log.i(TAG, "VideoEncoder()");

//        this.mStartTime = startTime;
        mStartTime = System.nanoTime();
        this.encoderCallback = callback;
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

/*
        File file = getOutputMediaFile();
        Log.i(TAG, "videofile: " + file.getName());

        try {
            mMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mATrackIndex = mMuxer.addTrack(aMediaCodec.getOutputFormat());
        Log.i(TAG, "mATrackIndex: " + mATrackIndex);
*/

        mTrackIndex = -1;
        mMuxerStarted = false;

        mNV21 = new NV21Convertor();

        mNV21.setSize(this.vsize.width, this.vsize.height);
        mNV21.setSliceHeigth(this.vsize.height);
        mNV21.setStride(this.vsize.width);
        mNV21.setYPadding(0);
        mNV21.setEncoderColorFormat(mColorFormat);

    }

    public MediaCodec getaMediaCodec(){
        return mMediaCodec;
    }

    public void encodeFrame(byte[] input/* , byte[] output */) {
        Log.i(TAG, "encodeFrame()");

        if(input == null)
            return;

        long encodedSize = 0;
        Log.i(TAG, "encodeFrame() " + mFrameData.toString() + " / " + mFrameData.length);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (VERBOSE)
            Log.i(TAG, "inputBufferIndex-->" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            long endTime = System.nanoTime();
            long ptsUsec = (endTime - mStartTime) / 1000;
            Log.i(TAG, "resentationTime: " + ptsUsec);
            mNV21.convert(input, inputBuffers[inputBufferIndex]);
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//            inputBuffer.clear();
//            inputBuffer.put(mFrameData);

            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.capacity(), System.nanoTime() / 1000, 0);
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
                mMuxer.start();
                mMuxerStarted = true;*/

                trackIndex = encoderCallback.getMuxer().addTrack(newFormat);
                encoderCallback.getMuxer().start();
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
                        mTrackIndex = encoderCallback.getMuxer().addTrack(newFormat);
                        encoderCallback.getMuxer().start();
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);


                    Log.d(TAG, "====H264 writeSampleData mTrackIndex : " + mTrackIndex + " / es : " + outputBuffer.capacity() + " / " + outputBuffer.limit() + " / " + outputBuffer.position());
                    Log.d(TAG, "====H264 writeSampleData bufferInfo : " + mBufferInfo.size + " / " + mBufferInfo.offset + " / " + mBufferInfo.presentationTimeUs + " / " + mBufferInfo.flags);

                    encoderCallback.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
                    if (VERBOSE) {
                        Log.d(TAG, "sent buffer1 " + mBufferInfo.size + " bytes to muxer");
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
        Log.i(TAG, "mMediaCodec close()");
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Returns a color format that is supported by the codec and by this test
     * code. If no match is found, this throws a test failure -- the set of
     * formats known to the test should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG,
                "couldn't find a good color format for " + codecInfo.getName()
                        + " / " + mimeType);
        return 0; // not reached
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

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    /**
     * Returns true if the specified color format is semi-planar YUV. Throws an
     * exception if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

    public void setTrackIndex(int trackIndex) {
        this.trackIndex = trackIndex;
    }
}
