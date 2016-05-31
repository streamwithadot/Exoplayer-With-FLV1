/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses video tags from an FLV stream and extracts H.264 nal units.
 */
/* package */ final class VideoTagPayloadReader extends TagPayloadReader {

  // Video codec.
  public static final int VIDEO_CODEC_H263 = 2;
  public static final int VIDEO_CODEC_AVC = 7;

  // Frame types.
  public static final int VIDEO_FRAME_INTRAFRAME = 1; // Keyframe
  public static final int VIDEO_FRAME_VIDEO_INFO = 5;

  // State variables.
  public int frameType;
  public int videoCodec;

  VideoPacketReader packetReader;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public VideoTagPayloadReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  public static int getCodec(ParsableByteArray data){
    int header = data.readUnsignedByte();
    return (header & 0x0F);
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
    int header = data.readUnsignedByte();
    frameType = (header >> 4) & 0x0F;
    videoCodec = (header & 0x0F);
    // Support just H.264 and Sorenson H.263 encoded content.
    if(packetReader == null){
      if(videoCodec == VIDEO_CODEC_AVC){
        packetReader = new AVCPacketReader();
      } else if(videoCodec == VIDEO_CODEC_H263){
        packetReader = new H263PacketReader();
      } else {
        throw new UnsupportedFormatException("Video format not supported: " + videoCodec);
      }
    }
    return (frameType != VIDEO_FRAME_VIDEO_INFO);
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
    //  The designated packet reader will call output.sampleData() and output.sampleMetadata().
    packetReader.parsePayload(this, data, timeUs);
  }

  public interface VideoPacketReader{
    void parsePayload(VideoTagPayloadReader reader, ParsableByteArray data, long timeUs)
            throws ParserException;
  }
}
