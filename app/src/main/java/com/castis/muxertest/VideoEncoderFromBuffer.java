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
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex = -1;
    private boolean mMuxerStarted;
    byte[] mFrameData;
    FileOutputStream mFileOutputStream = null;
    private int mColorFormat;
    private long mStartTime = 0;

    private int vcolor;
    private MediaCodecInfo vmci;
    private static final String VCODEC = "video/avc";

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
    public VideoEncoderFromBuffer(android.hardware.Camera.Size size) {
        Log.i(TAG, "VideoEncoder()");
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

        mStartTime = System.nanoTime();

        try {
            mMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
        mTrackIndex = -1;
        mMuxerStarted = false;

        mNV21 = new NV21Convertor();

        mNV21.setSize(this.vsize.width, this.vsize.height);
        mNV21.setSliceHeigth(this.vsize.height);
        mNV21.setStride(this.vsize.width);
        mNV21.setYPadding(0);
        mNV21.setEncoderColorFormat(mColorFormat);
    }

    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    NV21Convertor mNV21 = null;


    /**
     * Generates data for frame N into the supplied buffer.  We have an 8-frame animation
     * sequence that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
     */
    private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {
        final int HALF_WIDTH = vsize.width / 2;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);
        // Set to zero.  In YUV this is a dull green.
        Arrays.fill(frameData, (byte) 0);
        int startX, startY, countX, countY;
        frameIndex %= 8;
        //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
        if (frameIndex < 4) {
            startX = frameIndex * (mWidth / 4);
            startY = 0;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = mHeight / 2;
        }
        for (int y = startY + (mHeight / 2) - 1; y >= startY; --y) {
            for (int x = startX + (mWidth / 4) - 1; x >= startX; --x) {
                if (semiPlanar) {
                    // full-size Y, followed by UV pairs at half resolution
                    // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                    // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                    //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x] = (byte) TEST_U;
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                    }
                } else {
                    // full-size Y, followed by quarter-size U and quarter-size V
                    // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                    // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_U;
                        frameData[mWidth * mHeight + HALF_WIDTH * (mHeight / 2) +
                                (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_V;
                    }
                }
            }
        }
    }


    public void encodeFrame(byte[] input, android.hardware.Camera.Parameters parameters) {
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
        File file = Util.getOutputMediaFile("bitmpa", "bitmap2.jpg");
        Util.SaveBitmapToFileCache(bm, file.getAbsolutePath());


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
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
                    mFrameData.length, System.nanoTime() / 1000, 0);
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
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
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
                        mTrackIndex = mMuxer.addTrack(newFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
                    if (VERBOSE) {
                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                    }
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }

    public void encodeFrame(byte[] input/* , byte[] output */) {
        Log.i(TAG, "encodeFrame()");
        long encodedSize = 0;

        /*if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            YV12toYUV420Planar(input, mFrameData, vsize.width, vsize.height);
        } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
            YV12toYUV420PackedSemiPlanar(input, mFrameData, vsize.width, vsize.height);
        } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            YV12toYUV420PackedSemiPlanar(input, mFrameData, vsize.width, vsize.height);
        } else {
            System.arraycopy(input, 0, mFrameData, 0, mFrameData.length);
        }*/


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
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
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
                        mTrackIndex = mMuxer.addTrack(newFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

//					write raw data
//					byte[] outData = new byte[bufferInfo.size];
//					outputBuffer.get(outData);
//					outputBuffer.position(bufferInfo.offset);
//					try {
//						mFileOutputStream.write(outData);
//						Log.i(TAG, "output data size -- > " + outData.length);
//					} catch (IOException ioe) {
//						Log.w(TAG, "failed writing debug data to file");
//						throw new RuntimeException(ioe);
//					}
                    mMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
                    if (VERBOSE) {
                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
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
        if (mMuxer != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
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
     * NV21 is a 4:2:0 YCbCr, For 1 NV21 pixel: YYYYYYYY VUVU I420YUVSemiPlanar
     * is a 4:2:0 YUV, For a single I420 pixel: YYYYYYYY UVUV Apply NV21 to
     * I420YUVSemiPlanar(NV12) Refer to https://wiki.videolan.org/YUV/
     */
    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
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

}
