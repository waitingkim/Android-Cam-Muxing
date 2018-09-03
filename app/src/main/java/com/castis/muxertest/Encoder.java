package com.castis.muxertest;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;

public interface Encoder {

    public void writeSampleData(int trackIndex, ByteBuffer writeByteBuffer, MediaCodec.BufferInfo bufferInfo);

    public MediaMuxer getMuxer();

}
