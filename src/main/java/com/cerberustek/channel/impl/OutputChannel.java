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
import com.cerberustek.buffer.Buffer;
import com.cerberustek.buffer.BufferOutputStream;
import com.cerberustek.buffer.OverflowBuffer;
import com.cerberustek.channel.NetChannel;
import com.cerberustek.channel.NetPipeline;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetDisconnectionEvent;

import java.io.*;
import java.nio.ByteBuffer;

public class OutputChannel extends BufferOutputStream implements NetChannel, DataOutput {

    private final short channelId;
    private final NetPipeline pipeline;
    private final NetValve valve;
    private final Buffer buffer;

    private int packetCounter = 0;

    private byte[] bytearr = null;

    public OutputChannel(short channelId, int bufferCap, NetPipeline pipeline, NetValve valve) {
        this(channelId, new OverflowBuffer(bufferCap), pipeline, valve);
    }

    private OutputChannel(short channelId, Buffer buffer, NetPipeline pipeline, NetValve valve) {
        super(buffer);
        this.channelId = channelId;
        this.pipeline = pipeline;
        this.valve = valve;
        this.buffer = buffer;
    }

    public void update() {
        int available = buffer.remaining();

        while (available > 0) {
//            if (channelId == 3)
//                CerberusRegistry.getInstance().debug("Sending message: " + packetCounter);
            final int off = 8;

            byte[] packet = new byte[valve.getNetworkBufferSize() + off];
            int length = buffer.read(packet, off, valve.getNetworkBufferSize());
            if (length > 0) {
//                if (channelId == 3)
//                    System.out.println("Packet size: " + length);

                ByteBuffer byteBuffer = ByteBuffer.allocate(off);
                byteBuffer.putShort(channelId);
                byteBuffer.putShort((short) (packetCounter + 1));
                byteBuffer.put((byte) (length >>> 24));
                byteBuffer.put((byte) (length >>> 16));
                byteBuffer.put((byte) (length >>> 8));
                byteBuffer.put((byte) length);

                byteBuffer.rewind();
                byteBuffer.get(packet, 0, off);

                try {
                    // System.out.println("Writing " + (length + off) + " bytes to channel with id: " + channelId + "!");
                    pipeline.write(packet, 0, length + off);
                } catch (IOException | ArrayIndexOutOfBoundsException e) {
                    CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                            new NetDisconnectionEvent(valve, e));
                    try {
                        valve.getPipeline().close();
                    } catch (IOException e1) {
                        // ignore
                    }
                }
            }

            packetCounter++;
            available = buffer.remaining();
        }
    }

    @Override
    public void write(int b) {
        if (buffer.capacity() - buffer.remaining() < 1)
            update();

        super.write(b);
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        while (len > 0) {
            int writeAble = buffer.capacity() - buffer.remaining();
            if (writeAble > len) {
                super.write(b, off, len);
                len = 0;
            } else {
                super.write(b, off, writeAble);
                len -= writeAble;
                off += writeAble;

                update();
            }
        }
    }

    @Override
    public short getChannelId() {
        return channelId;
    }

    @Override
    public synchronized void flush() {
        update();
        packetCounter = 0;
    }

    @Override
    public void writeBoolean(boolean v) {
        write(v ? 1 : 0);
    }

    @Override
    public void writeByte(int v) {
        if (buffer.capacity() - buffer.remaining() < 1)
            update();

        write(v);
    }

    @Override
    public void writeShort(int v) {
        write((v >>> 8) & 0xFF);
        write(v & 0xFF);
    }

    @Override
    public void writeChar(int v) {
        write((v >>> 8) & 0xFF);
        write(v & 0xFF);
    }

    @Override
    public void writeInt(int v) {
        write((v >>> 24) & 0xFF);
        write((v >>> 16) & 0xFF);
        write((v >>> 8) & 0xFF);
        write(v & 0xFF);
    }

    private byte[] writtenBuffer = new byte[8];
    @Override
    public void writeLong(long v) {
        writtenBuffer[0] = (byte) (v >>> 56);
        writtenBuffer[1] = (byte) (v >>> 48);
        writtenBuffer[2] = (byte) (v >>> 40);
        writtenBuffer[3] = (byte) (v >>> 32);
        writtenBuffer[4] = (byte) (v >>> 24);
        writtenBuffer[5] = (byte) (v >>> 16);
        writtenBuffer[6] = (byte) (v >>> 8);
        writtenBuffer[7] = (byte) v;
        write(writtenBuffer);
    }

    @Override
    public void writeFloat(float v) {
        writeInt(Float.floatToIntBits(v));
    }

    @Override
    public void writeDouble(double v) {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void writeBytes(String s) {
        int length = s.length();
        for (int index = 0; index < length; index++)
            writeByte((byte) s.charAt(index));
    }

    @Override
    public void writeChars(String s) {
        char[] chars = s.toCharArray();
        for (char c : chars)
            writeChar(c);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        writeUTF(s, this);
    }

    private static int writeUTF(String str, DataOutput out) throws IOException {
        int strlen = str.length();
        int utflen = 0;
        int c, count = 0;

        /* use charAt instead of copying String to char array */
        for (int i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        if (utflen > 65535)
            throw new UTFDataFormatException(
                    "encoded string too long: " + utflen + " bytes");

        byte[] bytearr;
        if (out instanceof OutputChannel) {
            OutputChannel dos = (OutputChannel) out;
            if(dos.bytearr == null || (dos.bytearr.length < (utflen+2)))
                dos.bytearr = new byte[(utflen*2) + 2];
            bytearr = dos.bytearr;
        } else {
            bytearr = new byte[utflen+2];
        }

        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) ((utflen) & 0xFF);

        int i;
        for (i=0; i<strlen; i++) {
            c = str.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) break;
            bytearr[count++] = (byte) c;
        }

        for (;i < strlen; i++){
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytearr[count++] = (byte) c;

            } else if (c > 0x07FF) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >>  6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c) & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c) & 0x3F));
            }
        }
        out.write(bytearr, 0, utflen+2);
        return utflen + 2;
    }

    @Override
    public boolean empty() {
        return buffer.remaining() <= 0;
    }

    public Buffer getBuffer() {
        return buffer;
    }
}
