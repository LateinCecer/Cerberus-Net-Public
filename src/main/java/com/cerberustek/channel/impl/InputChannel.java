/*
 * Cerberus-Net is a simple network library based on the java socket
 * framework. It also includes a powerful scheduling solution.
 * Visit https://cerberustek.com for more details
 * Copyright (c)  2020  Adrian Paskert
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. See the file LICENSE included with this
 * distribution for more information.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.cerberustek.channel.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.buffer.DataBuffer;
import com.cerberustek.buffer.DataBufferInputStream;
import com.cerberustek.buffer.OverflowBuffer;
import com.cerberustek.channel.NetChannel;

import java.io.*;

public class InputChannel extends DataBufferInputStream implements NetChannel, DataInput {

    private final short channelId;
    private final DataBuffer buffer;

    private int timeOut = -1;

    private byte bytearr[] = new byte[80];
    private char chararr[] = new char[80];

    private int currentBatchIndex = 0;
    private int leftToRead = 0;
    private boolean readable = false;

    private CerberusEvent eventService;

    public InputChannel(short channelId, int bufferCap) {
        this(channelId, new OverflowBuffer(bufferCap));
    }

    private InputChannel(short channelId, DataBuffer buffer) {
        super(buffer);
        this.channelId = channelId;
        this.buffer = buffer;
    }

    @Override
    public short getChannelId() {
        return channelId;
    }

    public void process(byte[] data, int off, int len) {
        if (leftToRead == 0) {
            int batchIndex = (data[off] & 0xFF) << 8 | (data[off + 1] & 0xFF);
            int batchSize = data[off + 2] << 24 | ((data[off + 3] & 0xFF) << 16) | ((data[off + 4] & 0xFF) << 8) | (data[off + 5] & 0xFF);
//
//            if (channelId == 3)
//                CerberusRegistry.getInstance().debug("batch index: " + batchIndex + " @ " + batchSize + " bytes, len=" + len + "  --  " + data[off + 2] + ":" + data[off + 3] + ":" + data[off + 4] + ":" + data[off + 5]);

            if (batchIndex == 1) {
                currentBatchIndex = batchIndex;
//            CerberusRegistry.getInstance().fine("batch index: " + batchIndex);
                // CerberusRegistry.getInstance().fine("batch size: " + batchSize);
                buffer.mark();
                saveWrite(data, 6 + off, batchSize);
                // System.out.println("Data: " + Arrays.toString(data));
                readable = true;
            } else {

                if (batchIndex != currentBatchIndex + 1) {
                    CerberusRegistry.getInstance().warning("Lost packet! Missing part! " + batchIndex + " != " + (currentBatchIndex + 1));
                    if (buffer.hasMark())
                        buffer.reset();
                    else
                        buffer.clear();

                    currentBatchIndex = 0;
                } else {
                    currentBatchIndex++;
                    saveWrite(data, 6 + off, batchSize);
                }
                readable = false;
            }

            leftToRead = batchSize - len + 6;
            if (leftToRead < 0) {
                int tmp = -leftToRead;
                leftToRead = 0;
                process(data, off + 6 + batchSize, tmp);
            }
        } else {

            if (len > leftToRead) {
                saveWrite(data, off, leftToRead);
                readable = currentBatchIndex == 1;
                int tmp = leftToRead;
                leftToRead = 0;
                process(data, off + tmp, len - tmp);
            } else {
                readable = len == leftToRead && currentBatchIndex == 1;
                saveWrite(data, off, len);
                leftToRead -= len;
            }
        }
    }

    private void saveWrite(byte[] data, int off, int len) {
        try {
            buffer.write(data, off, len);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            buffer.clear();
        }
    }

    public boolean isPackageStart() {
        return readable;
    }

    public void skipFully(long l) throws IOException {
        try {
            buffer.skipFully((int) l);
        } catch (InterruptedException e) {
            throw new IOException();
        }
    }

    public void readFully(int halt, byte[] b) throws InterruptedException {
        buffer.readFully(halt, b);
    }

    public void readFully(int halt, byte[] b, int len) throws InterruptedException {
        buffer.readFully(halt, b, 0, len);
    }

    public void readFully(int halt, byte[] b, int off, int len) throws InterruptedException {
        buffer.readFully(halt, b, off, len);
    }

    public void skipFully(int halt, long l) {
        try {
            buffer.skipFully(halt, (int) l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean readBoolean(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        if (i < 0)
            throw new EOFException();
        return i != 0;
    }

    public byte readByte(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        if (i < 0)
            throw new EOFException();
        return (byte) i;
    }

    public int readUnsignedByte(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        if (i < 0)
            throw new EOFException();
        return i;
    }

    public short readShort(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        int j = buffer.readFully(halt);

        if ((i | j) < 0)
            throw new EOFException();
        return (short) ((i << 8) + j);
    }

    public int readUnsignedShort(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        int j = buffer.readFully(halt);

        if ((i | j) < 0)
            throw new EOFException();
        return ((i << 8) + j);
    }

    public char readChar(int halt) throws EOFException, InterruptedException {
        return (char)readUnsignedShort(halt);
    }

    public int readInt(int halt) throws EOFException, InterruptedException {
        int i = buffer.readFully(halt);
        int j = buffer.readFully(halt);
        int k = buffer.readFully(halt);
        int l = buffer.readFully(halt);

        if ((i | j | k | l) < 0)
            throw new EOFException();
        return (i << 24) + (j << 16) + (k << 8) + l;
    }

    private byte[] readBuffer = new byte[8];

    public long readLong(int halt) throws InterruptedException {
        buffer.readFully(halt, readBuffer);
        return (((long)readBuffer[0] << 56) +
                ((long)(readBuffer[1] & 255) << 48) +
                ((long)(readBuffer[2] & 255) << 40) +
                ((long)(readBuffer[3] & 255) << 32) +
                ((long)(readBuffer[4] & 255) << 24) +
                ((readBuffer[5] & 255) << 16) +
                ((readBuffer[6] & 255) <<  8) +
                (readBuffer[7] & 255));
    }

    public float readFloat(int halt) throws EOFException, InterruptedException {
        return Float.intBitsToFloat(readInt(halt));
    }

    public double readDouble(int halt) throws EOFException, InterruptedException {
        return Double.longBitsToDouble(readLong(halt));
    }

    private char[] lineBuffer;

    @SuppressWarnings("Duplicates")
    @Deprecated
    @Override
    public String readLine() throws EOFException {
        if (timeOut >= 0)
            return readLine(timeOut);

        char[] buffer = lineBuffer;
        if (buffer == null) {
            buffer = lineBuffer = new char[128];
        }

        int room = buffer.length;
        int offset = 0;
        int current;

        Loop : while (true) {
            try {
                switch (current = this.buffer.readFully()) {
                    case -1:
                    case '\n':
                        break Loop;
                    default:
                        if (--room < 0) {
                            buffer = new char[offset + 128];
                            room = buffer.length - offset - 1;
                            System.arraycopy(lineBuffer, 0, buffer, 0, offset);
                            lineBuffer = buffer;
                        }
                        buffer[offset++] = (char) current;
                }
            } catch (InterruptedException e) {
                throw new EOFException();
            }
        }

        if (current == -1)
            return null;
        return String.copyValueOf(buffer, 0, offset);
    }

    @SuppressWarnings("Duplicates")
    public String readLine(int halt) throws EOFException {
        char[] buffer = lineBuffer;
        if (buffer == null) {
            buffer = lineBuffer = new char[128];
        }

        int room = buffer.length;
        int offset = 0;
        int current;

        Loop : while (true) {
            try {
                switch (current = this.buffer.readFully(halt)) {
                    case -1:
                    case '\n':
                        break Loop;
                    default:
                        if (--room < 0) {
                            buffer = new char[offset + 128];
                            room = buffer.length - offset - 1;
                            System.arraycopy(lineBuffer, 0, buffer, 0, offset);
                            lineBuffer = buffer;
                        }
                        buffer[offset++] = (char) current;
                }
            } catch (InterruptedException e) {
                throw new EOFException();
            }
        }

        if (current == -1)
            return null;
        return String.copyValueOf(buffer, 0, offset);
    }

    @Override
    public String readUTF() throws IOException {
        if (timeOut >= 0) {
            try {
                return readUTF(timeOut);
            } catch (InterruptedException e) {
                throw new IOException();
            }
        }

        try {
            return readUTF(-1, this);
        } catch (InterruptedException e) {
            throw new IOException();
        }
    }

    public String readUTF(int halt) throws IOException, InterruptedException {
        return readUTF(halt, this);
    }

    private static String readUTF(int halt, InputChannel in) throws IOException, InterruptedException {
        int utflen = halt < 0 ? in.readUnsignedShort() : in.readUnsignedShort(halt);
        byte[] bytearr;
        char[] chararr;

        if (in.bytearr.length < utflen){
            in.bytearr = new byte[utflen*2];
            in.chararr = new char[utflen*2];
        }
        chararr = in.chararr;
        bytearr = in.bytearr;


        int c, char2, char3;
        int count = 0;
        int chararr_count=0;

        in.readFully(bytearr, 0, utflen);

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            if (c > 127) break;
            count++;
            chararr[chararr_count++]=(char)c;
        }

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                    /* 0xxxxxxx*/
                    count++;
                    chararr[chararr_count++]=(char)c;
                    break;
                case 12: case 13:
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    char2 = (int) bytearr[count-1];
                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException(
                                "malformed input around byte " + count);
                    chararr[chararr_count++]=(char)(((c & 0x1F) << 6) |
                            (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    char2 = (int) bytearr[count-2];
                    char3 = (int) bytearr[count-1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (count-1));
                    chararr[chararr_count++]=(char)(((c     & 0x0F) << 12) |
                            ((char2 & 0x3F) << 6)  |
                            (char3 & 0x3F));
                    break;
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException(
                            "malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararr_count);
    }

    public void clear() {
        buffer.clear();
    }

    @Override
    public int available() {
        return buffer.remaining();
    }

    @Override
    public boolean empty() {
        return buffer.remaining() <= 0;
    }

    public DataBuffer getBuffer() {
        return buffer;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    private CerberusEvent getEventService() {
        if (eventService == null)
            eventService = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        return eventService;
    }
}
