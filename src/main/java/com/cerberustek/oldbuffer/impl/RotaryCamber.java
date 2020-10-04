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

package com.cerberustek.oldbuffer.impl;

import com.cerberustek.oldbuffer.Buffer;

public class RotaryCamber implements Buffer {

    private int readHead = 0;
    private int writeHead = 0;

    private final byte[] data;

    public RotaryCamber(int capacity) {
        data = new byte[capacity];
    }

    @Override
    public int write(byte[] bufferData) {
        return write(bufferData, 0, bufferData.length);
    }

    @Override
    public int write(byte[] bufferData, int length) {
        return write(bufferData, 0, length);
    }

    @Override
    public int write(byte[] bufferData, int offset, int length) {
        int i = data.length - writeHead;
        if (i < length) {
            System.arraycopy(bufferData, offset, data, writeHead, i);
            writeHead += i;
            return i;
        }
        System.arraycopy(bufferData, offset, data, writeHead, length);
        writeHead += length;

        synchronized (this) {
            this.notifyAll();
        }
        return length;
    }

    @Override
    public int read(byte[] bufferData) {
        return read(bufferData, 0, bufferData.length);
    }

    @Override
    public int read(byte[] bufferData, int length) {
        return read(bufferData, 0, length);
    }

    @Override
    public int read(byte[] bufferData, int offset, int length) {
        int i = writeHead - readHead;
        if (i < length) {
            System.arraycopy(data, readHead, bufferData, offset, i);
            readHead += i;
            return i;
        } else {
            System.arraycopy(data, readHead, bufferData, offset, length);
            readHead += length;
            return length;
        }
    }

    @Override
    public int readFully(int halt, byte[] bufferData) throws InterruptedException {
        return readFully(halt, bufferData, 0, bufferData.length);
    }

    @Override
    public int readFully(int halt, byte[] bufferData, int length) throws InterruptedException {
        return readFully(halt, bufferData, 0, length);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public int readFully(int halt, byte[] bufferData, int offset, int length) throws InterruptedException {
        int available = remaining();
        if (available < length && halt > 0) {

            long startTime = System.currentTimeMillis();
            synchronized (this) {
                this.wait(halt);
            }
            return readFully((int) (halt - (System.currentTimeMillis() - startTime)), bufferData, offset, length);
        }
        return read(bufferData, offset, length);
    }

    @Override
    public int readFully(byte[] bufferData) throws InterruptedException {
        return this.readFully(bufferData, 0, bufferData.length);
    }

    @Override
    public int readFully(byte[] bufferData, int length) throws InterruptedException {
        return this.readFully(bufferData, 0, length);
    }

    @Override
    public int readFully(byte[] bufferData, int offset, int length) throws InterruptedException {
        int available = remaining();
        if (available < length) {

            synchronized (this) {
                this.wait();
            }
            return this.readFully(bufferData, offset, length);
        }
        return read(bufferData, offset, length);
    }

    @Override
    public int skip(int length) {
        int i = remaining();
        if (i < length) {
            readHead += i;
            return i;
        }
        readHead += length;
        return length;
    }

    @Override
    public int skipFully(int length) throws InterruptedException {
        int i = remaining();

        if (i < length) {

            if (writeHead < data.length) {

                synchronized (this) {
                    wait();
                }
                return skipFully(length);
            } else {
                readHead = data.length;
                return i;
            }
        }
        return skip(length);
    }

    @Override
    public int skipFully(int halt, int length) throws InterruptedException {
        int i = remaining();

        if (i < length && halt > 0) {

            if (writeHead < data.length) {

                long startTime = System.currentTimeMillis();
                synchronized (this) {
                    wait(halt);
                }
                return skipFully((int) (halt - (System.currentTimeMillis() - startTime)), length);
            } else {
                readHead = data.length;
                return i;
            }
        }
        return skip(length);
    }

    public int getReadHead() {
        return readHead;
    }

    public int getWriteHead() {
        return writeHead;
    }

    @Override
    public int remaining() {
        return writeHead - readHead;
    }

    @Override
    public int sizeof() {
        return data.length;
    }

    public void resetReadHead() {
        readHead = 0;
    }

    public void resetWriteHead() {
        writeHead = 0;
    }
}
