package com.gotye.bibo.util;

import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by Michael.Ma on 2016/2/14.
 */
public class Mp4Process {
    private final static String TAG = "Mp4Process";

    public interface IProcessProgress {
        abstract void OnProgress(long processed, long totalsize);
    }

    public static void fastPlay(String srcFile, String dstFile, @Nullable IProcessProgress cbProgress)  {
        RandomAccessFile inFile = null;
        FileOutputStream outFile = null;
        try {
            inFile = new RandomAccessFile(new File(srcFile), "r");
            int moovPos = 0;
            int mdatPos = 0;
            int moovSize = 0;
            int mdatSize = 0;
            byte[] boxSizeBuf = new byte[4];
            byte[] pathBuf = new byte[4];
            int boxSize;
            int dataSize;
            int bytesRead;
            int totalBytesRead = 0;
            int bytesWritten = 0;

            // First find the location and size of the moov and mdat boxes
            while (true) {
                try {
                    boxSize = inFile.readInt();
                    bytesRead = inFile.read(pathBuf);
                    if (bytesRead != 4) {
                        LogUtil.error(TAG, "Unexpected bytes read (path) " + bytesRead);
                        break;
                    }
                    String pathRead = new String(pathBuf, "UTF-8");
                    dataSize = boxSize - 8;
                    totalBytesRead += 8;
                    LogUtil.info(TAG, "box type: " + pathRead);
                    if (pathRead.equals("moov")) {
                        moovPos = totalBytesRead - 8;
                        moovSize = boxSize;
                        LogUtil.info(TAG, String.format("moov pos %d, size %d", moovPos, moovSize));
                    } else if (pathRead.equals("mdat")) {
                        mdatPos = totalBytesRead - 8;
                        mdatSize = boxSize;
                        LogUtil.info(TAG, String.format("mdat pos %d, size %d", mdatPos, mdatSize));
                    }
                    totalBytesRead += inFile.skipBytes(dataSize);
                } catch (IOException e) {
                    break;
                }
            }

            if (moovPos > 0 && mdatPos > 0 && mdatPos > moovPos) {
                LogUtil.warn(TAG, "this file is already a streamable mp4 file: " + srcFile);
                return;
            }

            outFile = new FileOutputStream(new File(dstFile));

            // Read the moov box into a buffer. This has to be patched up. Ug.
            inFile.seek(moovPos);
            byte[] moovBoxBuf = new byte[moovSize]; // This shouldn't be too big.
            bytesRead = inFile.read(moovBoxBuf);
            if (bytesRead != moovSize) {
                LogUtil.error(TAG, "Couldn't read full moov box");
            }

            // Now locate the stco boxes (chunk offset box) inside the moov box and patch
            // them up. This ain't purdy.
            int pos = 0;
            while (pos < moovBoxBuf.length - 4) {
                if (moovBoxBuf[pos] == 0x73 && moovBoxBuf[pos + 1] == 0x74 &&
                        moovBoxBuf[pos + 2] == 0x63 && moovBoxBuf[pos + 3] == 0x6f) {
                    int stcoPos = pos - 4;
                    int stcoSize = byteArrayToInt(moovBoxBuf, stcoPos);
                    patchStco(moovBoxBuf, stcoSize, stcoPos, moovSize);
                }
                pos++;
            }

            inFile.seek(0);
            byte[] buf = new byte[(int) mdatPos];
            // Write out everything before mdat
            inFile.read(buf);
            outFile.write(buf);

            // Write moov
            outFile.write(moovBoxBuf, 0, moovSize);

            // Write out mdat
            inFile.seek(mdatPos);
            bytesWritten = 0;
            while (bytesWritten < mdatSize) {
                int bytesRemaining = (int) mdatSize - bytesWritten;
                int bytesToRead = buf.length;
                if (bytesRemaining < bytesToRead) bytesToRead = bytesRemaining;
                bytesRead = inFile.read(buf, 0, bytesToRead);
                if (bytesRead > 0) {
                    outFile.write(buf, 0, bytesRead);
                    bytesWritten += bytesRead;
                    if (cbProgress != null) {
                        cbProgress.OnProgress(mdatPos + moovSize + bytesWritten, inFile.length());
                    }
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            LogUtil.error(TAG, e.getMessage());
        } finally {
            try {
                if (outFile != null) outFile.close();
                if (inFile != null) inFile.close();
            } catch (IOException e) {}
        }
    }

    private static void patchStco(byte[] buf, int size, int pos, int moovSize) {
        LogUtil.info(TAG, "stco pos: " + pos + ", size " + size);
        // We are inserting the moov box before the mdat box so all of
        // offsets in the stco box need to be increased by the size of the moov box. The stco
        // box is variable in length. 4 byte size, 4 byte path, 4 byte version, 4 byte flags
        // followed by a variable number of chunk offsets. So subtract off 16 from size then
        // divide result by 4 to get the number of chunk offsets to patch up.
        int chunkOffsetCount = (size - 16) / 4;
        int chunkPos = pos + 16;
        for (int i = 0; i < chunkOffsetCount; i++) {
            int chunkOffset = byteArrayToInt(buf, chunkPos);
            int newChunkOffset = chunkOffset + moovSize;
            intToByteArray(newChunkOffset, buf, chunkPos);
            chunkPos += 4;
        }
    }

    private static int byteArrayToInt(byte[] b, int offset) {
        return   b[offset + 3] & 0xFF |
                (b[offset + 2] & 0xFF) << 8 |
                (b[offset + 1] & 0xFF) << 16 |
                (b[offset] & 0xFF) << 24;
    }

    private static void intToByteArray(int a, byte[] buf, int offset) {
        buf[offset] = (byte) ((a >> 24) & 0xFF);
        buf[offset + 1] = (byte) ((a >> 16) & 0xFF);
        buf[offset + 2] = (byte) ((a >> 8) & 0xFF);
        buf[offset + 3] = (byte) (a  & 0xFF);
    }
}
