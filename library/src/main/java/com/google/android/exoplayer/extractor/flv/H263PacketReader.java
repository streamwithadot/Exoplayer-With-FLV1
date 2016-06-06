package com.google.android.exoplayer.extractor.flv;

import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import java.util.LinkedList;

/**
 * This will parse the AVC video tag within the flv container.
 */
@SuppressWarnings("SpellCheckingInspection")
public class H263PacketReader implements VideoTagPayloadReader.VideoPacketReader{
    // Packet types.
    private boolean hasOutputFormat;
    private int origWidth;
    private int origHeight;


    H263PacketReader(){
        hasOutputFormat = false;
    }

    @Override
    public void parsePayload(VideoTagPayloadReader reader, ParsableByteArray data, long timeUs)
            throws ParserException {
        // Extract some information about the FLV1 stream from the first few bits of the byte array.
        H263PictureData info = new H263PictureData(data);
        //  Just to be safe (totally not necessary), take what the flv tag says.
        if(((info.frameType == 0) ? 0 : 1) !=
                (reader.frameType == VideoTagPayloadReader.VIDEO_FRAME_INTRAFRAME ? 0 : 1)) {
            Log.e("PacketReader", "Frame type doesn't match!");
        }
        info.frameType = (reader.frameType == VideoTagPayloadReader.VIDEO_FRAME_INTRAFRAME ? 0 : 1);

        //  Create a new MediaFormat for the MediaCodec to start decoding this stream.
        if(!hasOutputFormat) {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_H263,
                    MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, reader.getDurationUs(),
                    info.width,
                    info.height,
                    new LinkedList<byte[]>(), MediaFormat.NO_VALUE,
                    MediaFormat.NO_VALUE);
            reader.output.format(mediaFormat);
            hasOutputFormat = true;
            //  Consume the rest and wait for next frame
            data.setPosition(data.limit());
            origWidth = info.width;
            origHeight = info.height;
        }

        if(origHeight != info.height || origWidth != info.width){
            Log.e("PacketReader", "Width or height has changed mid stream!");
        }

        ParsableByteArray convertedData = convertFLV1ToH263(data, info);
        int len = convertedData.bytesLeft();
        reader.output.sampleData(convertedData, len);
        reader.output.sampleMetadata(timeUs,
                info.frameType == 0 ? C.SAMPLE_FLAG_SYNC : 0,
                len, 0, null);
    }

    /**=
     * The H263 that is enclosed in an FLV stream is Sorenson Spark's implementation of
     * H263, which slightly differs from the original H263 implementation. This will make the
     * necessary changes to convert that.
     *
     * H263 Specification: T-REC-H.263-199603-S
     *
              CIF:   FLV1: 00000000 00000000 10000vaa aaaaaa01 0iidqqqq q0mmmmmm m...
          640x360:   FLV1: 00000000 00000000 10000vaa aaaaaa00 1wwwwwww wwwwwwww whhhhhhh hhhhhhhh hiidqqqq q0mmmmmm ...
                               0         1      2         3        4        5       6        7       8         9
         v - version (0 or 1)
         i - intra/inter/disposable-inter (0 or 1 or 2)
         d - deblocking flag (0 or 1)
         q - quantizer bits (0 to 32)
         m - macroblockdata


     INTER FRAME:   H263: 00000000 00000000 100000aa aaaaaa10 00011100 11100000 00000001 00000i00 10010|111 10100111 11100101 10100001 00000000 1001|qqqq q0mmmmmmm
                              0       1        2        3        4         5       6         7        8      9(new)   10(new)  11(new)  12(new)  13(new)

     INTRA FRAME:   H263: 00000000 00000000 100000aa aaaaaa10 00011100 11100000 00000001 00000i00 10010qqqq q0mmmmmmm
                              0       1        2        3        4         5       6      7(new)   8(new)


         a - temporal ref bits (0 to 256)
         i - intra/inter (0 or 1)
         q - quantizer bits (0 to 32)
         m - macroblockdata
     */
    private ParsableByteArray convertFLV1ToH263(ParsableByteArray data, H263PictureData info){
        /*
          Make the following changes to convert flv1 to h263
          -Make new data array of length+5
          -Copy bytes 0 to 6 inclusive to new array[0 to 6]
          -Copy bytes 7 to length-1 inclusive to new array[12-length-1]
          -Bytes 7,8,9,10,11 will be empty
          -byte 2, change bit 2 to 0
          -byte 3, change bit 1,0 to 1,0
          -Set byte 4 to 28
          -Set byte 5 to -32
          -Set byte 6 to 1
          -Set byte 7 to 0 or 4 depending on intra or inter frame
          -Set byte 8 to -105
          -Set byte 9 to -89
          -Set byte 10 to -27
          -Set byte 11 to -95
          -Set byte 12 to 0
          -Set byte 13 to (quantizer >> 1) - 112
          -byte 14, change bit 6,7 to 0,(quantizer & 0x1)
       */
        int pos = data.getPosition();
        // h263 byte array will have one extra byte.
        byte[] h263ByteArray = new byte[data.data.length + 5];
        //  Copy bytes 0 to 6 from flv stream into new array.
        System.arraycopy(data.data, 0, h263ByteArray, 0, 9);
        //  Copy bytes 12 to length-1 inclusive to new array[12-length-1]
        System.arraycopy(data.data, 9, h263ByteArray, 14, data.data.length - 9);
        //  In byte 2, change bit 2 to 0
        h263ByteArray[pos+2] &= ~(1 << (2));
        //  In byte 3, change bit 1,0 to 1,0 respectively.
        h263ByteArray[pos+3] |= (1 << 1);
        h263ByteArray[pos+3] &= ~(1);
        //  Byte 4 is 28
        h263ByteArray[pos+4] = 28;
        //  Byte 5 is -32
        h263ByteArray[pos+5] = (byte)-32;
        //  Byte 6 is 1
        h263ByteArray[pos+6] = 1;
        //  Byte 7 is 0 or 4 depending on intra or inter frame
        h263ByteArray[pos+7] = (byte)(info.frameType == 0 ? 0 : 4);
        //  Byte 8 is -105
        h263ByteArray[pos+8] = -105;
        //  Byte 9 is -89
        h263ByteArray[pos+9] = -89;
        //  Byte 10 is -42
        h263ByteArray[pos+10] = -27;
        //  Byte 11 is -95
        h263ByteArray[pos+11] = -95;
        //  Byte 12 is 0
        h263ByteArray[pos+12] = 0;
        //  Byte 13 is (quantizer >> 1) - 112
        h263ByteArray[pos+13] = (byte)((info.quantizationParam >> 1) - 112);
        //  Byte 14, change bit 6,7 to 0
        h263ByteArray[pos+14] &= ~(1 << 6);
        if((info.quantizationParam & 0x1) == 1){
            h263ByteArray[pos+14] |= 1 << 7;
        } else {
            h263ByteArray[pos+14] &= ~(1 << 7);
        }



        //  Package it back into a ParsableByteArray and return it.
        ParsableByteArray newArray = new ParsableByteArray(h263ByteArray);
        newArray.setPosition(pos);
        return newArray;
    }

    /**
     * Holds data parsed from an Sequence Header video tag atom.
     */
    @SuppressWarnings("unused")
    public static final class H263PictureData {
        public long startCode, version, temporalRef;
        public int width, height, pictureSize, frameType,
                    deblockingFlag, quantizationParam, extraInfoFlag;

        private int pendingBytes, startPos;

        /**
         * Extract some information about the FLV1 stream from the first few bits of the byte array.
         */
        public H263PictureData(ParsableByteArray data) {
            //  Save where we are in the ByteStream.
            startPos = data.getPosition();

            //  Read first 16 bytes
            long headerData = data.readLong();
            long headerDataNext = data.readLong();

            //  First 17 bits is start code
            startCode = (headerData >> (64-17)) & 0xffff;
            //  5 bits for the version code
            version = (headerData >> (64-17-5)) & 0x1f;
            //  8 bits for the temporal reference bits.
            temporalRef = (headerData >> (64-17-5-8)) & 0x1f;
            //  3 bits for the picture size.
            pictureSize = (int)((headerData >> (64-17-5-8-3)) & 0x7);

            //  Keep track of how many bytes pending (depends on what pictureSize was)
            pendingBytes =  64-17-5-8-3;

            //  Number of width and height bits depend on the pictureSize.
            switch(pictureSize){
                case 0:
                    width = (int)((headerData >> (64-17-5-8-3-8)) & 0xff);
                    height = (int)((headerData >> (64-17-5-8-3-8-8)) & 0xff);
                    pendingBytes = 64-17-5-8-3-8-8;
                    break;
                case 1:
                    width = (int)((headerData >> (64-17-5-8-3-16)) & 0xffff);
                    height = (int)((headerData) & 0xffff) << 1;
                    headerData = headerDataNext;
                    pendingBytes = 64-1;
                    break;
                case 2:
                    width = 352; height = 288;
                    break;
                case 3:
                    width = 176; height = 144;
                    break;
                case 4:
                    width = 128; height = 96;
                    break;
                case 5:
                    width = 320; height = 240;
                    break;
                case 6:
                    width = 160; height = 120;
                    break;
                default:
                    width = 320; height = 240; // Shouldn't happen, 111 is reserved.
            }

            // Frame type is 2 bits (intra, inter, or disposable inter - 0,1 or 2).
            pendingBytes-=2;
            frameType = (int)((headerData >> (pendingBytes)) & 0x3);

            // 1-bit de-blocking flag
            pendingBytes-=1;
            deblockingFlag = (int)((headerData >> (pendingBytes)) & 0x1);

            // 5 bits Quantization parameter (at least 7 more bytes remaining lol)
            pendingBytes-=5;
            quantizationParam = (int)((headerData >> (pendingBytes)) & 0x1f);

            pendingBytes-=1;
            extraInfoFlag = (int)((headerData >> (pendingBytes)) & 0x1);

            //  Reset the position of the data array back to where it was.
            data.setPosition(startPos);
        }
    }
}
