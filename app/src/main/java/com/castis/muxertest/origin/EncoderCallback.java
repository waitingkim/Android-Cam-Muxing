package com.castis.muxertest.origin;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;

public interface EncoderCallback {

    public void writeSampleData(int trackIndex, ByteBuffer writeByteBuffer, MediaCodec.BufferInfo bufferInfo);

    public int addTrack(MediaFormat format);

    public void start();

    public void stop();

    public void release();

}
