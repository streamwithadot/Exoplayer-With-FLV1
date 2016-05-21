package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.NalUnitUtil;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.List;

/**
 * This will parse the AVC video tag within the flv container.
 */
public class AVCPacketReader implements VideoTagPayloadReader.VideoPacketReader{
    // Packet types.
    private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
    private static final int AVC_PACKET_TYPE_AVC_NALU = 1;
    private boolean hasOutputFormat;

    // Temporary arrays.
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private int nalUnitLengthFieldLength;

    AVCPacketReader(){
        nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        nalLength = new ParsableByteArray(4);
    }

    @Override
    public void parsePayload(VideoTagPayloadReader reader, ParsableByteArray data, long timeUs)
            throws ParserException {
        int packetType = data.readUnsignedByte();
        int compositionTimeMs = data.readUnsignedInt24();
        timeUs += compositionTimeMs * 1000L;
        // Parse avc sequence header in case this was not done before.
        if (packetType == AVC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
            ParsableByteArray videoSequence = new ParsableByteArray(new byte[data.bytesLeft()]);
            data.readBytes(videoSequence.data, 0, data.bytesLeft());

            AvcSequenceHeaderData avcData = parseAvcCodecPrivate(videoSequence);
            nalUnitLengthFieldLength = avcData.nalUnitLengthFieldLength;

            // Construct and output the format.
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_H264,
                    MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, reader.getDurationUs(), avcData.width,
                    avcData.height, avcData.initializationData, MediaFormat.NO_VALUE,
                    avcData.pixelWidthAspectRatio);
            reader.output.format(mediaFormat);
            hasOutputFormat = true;
        } else if (packetType == AVC_PACKET_TYPE_AVC_NALU) {
            // TODO: Deduplicate with Mp4Extractor.
            // Zero the top three bytes of the array that we'll use to parse nal unit lengths, in case
            // they're only 1 or 2 bytes long.
            byte[] nalLengthData = nalLength.data;
            nalLengthData[0] = 0;
            nalLengthData[1] = 0;
            nalLengthData[2] = 0;
            int nalUnitLengthFieldLengthDiff = 4 - nalUnitLengthFieldLength;
            // NAL units are length delimited, but the decoder requires start code delimited units.
            // Loop until we've written the sample to the track output, replacing length delimiters with
            // start codes as we encounter them.
            int bytesWritten = 0;
            int bytesToWrite;
            while (data.bytesLeft() > 0) {
                // Read the NAL length so that we know where we find the next one.
                data.readBytes(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
                nalLength.setPosition(0);
                bytesToWrite = nalLength.readUnsignedIntToInt();

                // Write a start code for the current NAL unit.
                nalStartCode.setPosition(0);
                reader.output.sampleData(nalStartCode, 4);
                bytesWritten += 4;

                // Write the payload of the NAL unit.
                reader.output.sampleData(data, bytesToWrite);
                bytesWritten += bytesToWrite;
            }
            reader.output.sampleMetadata(timeUs,
                    reader.frameType == VideoTagPayloadReader.VIDEO_FRAME_INTRAFRAME ? C.SAMPLE_FLAG_SYNC : 0,
                    bytesWritten, 0, null);
        }
    }

    /**
     * Builds initialization data for a {@link MediaFormat} from H.264 (AVC) codec private data.
     *
     * @return The AvcSequenceHeader data needed to initialize the video codec.
     * @throws ParserException If the initialization data could not be built.
     */
    private AvcSequenceHeaderData parseAvcCodecPrivate(ParsableByteArray buffer)
            throws ParserException {
        // TODO: Deduplicate with AtomParsers.parseAvcCFromParent.
        buffer.setPosition(4);
        int nalUnitLengthFieldLength = (buffer.readUnsignedByte() & 0x03) + 1;
        Assertions.checkState(nalUnitLengthFieldLength != 3);
        List<byte[]> initializationData = new ArrayList<>();
        int numSequenceParameterSets = buffer.readUnsignedByte() & 0x1F;
        for (int i = 0; i < numSequenceParameterSets; i++) {
            initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
        }
        int numPictureParameterSets = buffer.readUnsignedByte();
        for (int j = 0; j < numPictureParameterSets; j++) {
            initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
        }

        float pixelWidthAspectRatio = 1;
        int width = MediaFormat.NO_VALUE;
        int height = MediaFormat.NO_VALUE;
        if (numSequenceParameterSets > 0) {
            // Parse the first sequence parameter set to obtain pixelWidthAspectRatio.
            ParsableBitArray spsDataBitArray = new ParsableBitArray(initializationData.get(0));
            // Skip the NAL header consisting of the nalUnitLengthField and the type (1 byte).
            spsDataBitArray.setPosition(8 * (nalUnitLengthFieldLength + 1));
            NalUnitUtil.SpsData sps = NalUnitUtil.parseSpsNalUnit(spsDataBitArray);
            width = sps.width;
            height = sps.height;
            pixelWidthAspectRatio = sps.pixelWidthAspectRatio;
        }

        return new AvcSequenceHeaderData(initializationData, nalUnitLengthFieldLength,
                width, height, pixelWidthAspectRatio);
    }

    /**
     * Holds data parsed from an Sequence Header video tag atom.
     */
    private static final class AvcSequenceHeaderData {

        public final List<byte[]> initializationData;
        public final int nalUnitLengthFieldLength;
        public final float pixelWidthAspectRatio;
        public final int width;
        public final int height;

        public AvcSequenceHeaderData(List<byte[]> initializationData, int nalUnitLengthFieldLength,
                                     int width, int height, float pixelWidthAspectRatio) {
            this.initializationData = initializationData;
            this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
            this.pixelWidthAspectRatio = pixelWidthAspectRatio;
            this.width = width;
            this.height = height;
        }

    }
}
