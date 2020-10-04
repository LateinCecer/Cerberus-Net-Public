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

public class RotaryBuffer implements Buffer {

    private final int cap;

    private int readHead = 0;
    private int writeHead = 0;

    private final RotaryCamber[] cambers;

    public RotaryBuffer(int cambers, int capacity) {
        this.cambers = new RotaryCamber[cambers];
        this.cap = capacity;
        for (int i = 0; i < cambers; i++)
            this.cambers[i] = new RotaryCamber(capacity);
    }

    @Override
    public synchronized int write(byte[] bufferData) {
        return write(bufferData, 0, bufferData.length);
    }

    @Override
    public synchronized int write(byte[] bufferData, int length) {
        return write(bufferData, 0, length);
    }

    @Override
    public synchronized int write(byte[] bufferData, int offset, int length) {
        int written = cambers[writeHead].write(bufferData, offset, length);
        synchronized (this) {
            this.notifyAll();
        }

        if (written < length) {
            rotateWrite();
            return written + write(bufferData, offset + written, length - written);
        } else
            return written;
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
    public synchronized int read(byte[] bufferData, int offset, int length) {
        int read = cambers[readHead].read(bufferData, offset, length);
        if (read < length) {
            int prevCamb = readHead;
            if (rotateRead()) {
                cambers[prevCamb].resetReadHead();
                cambers[prevCamb].resetWriteHead();
            } else
                return read;
            return read + read(bufferData, offset + read, length - read);
        } else
            return read;
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
    public synchronized int readFully(int halt, byte[] bufferData, int offset, int length) throws InterruptedException {
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
    public synchronized int readFully(byte[] bufferData, int offset, int length) throws InterruptedException {
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
    public synchronized int skip(int length) {
        int read = cambers[readHead].skip(length);
        if (read < length) {
            int prevCamb = readHead;
            if (rotateRead()) {
                cambers[prevCamb].resetReadHead();
                cambers[prevCamb].resetWriteHead();
            } else
                return read;
            return read + skip(length - read);
        } else
            return read;
    }

    @Override
    public int skipFully(int length) throws InterruptedException {
        int i = remaining();
        if (i < length) {

            synchronized (this) {
                wait();
            }
            return skipFully(length);
        }
        return skip(length);
    }

    @Override
    public int skipFully(int halt, int length) throws InterruptedException {
        int i = remaining();
        if (i < length && halt > 0) {

            long startTime = System.currentTimeMillis();
            synchronized (this) {
                wait(halt);
            }
            return skipFully((int) (halt - (System.currentTimeMillis() - startTime)), length);
        }
        return skip(length);
    }

    @Override
    public int remaining() {
        int cambersRemaining;
        if (readHead > writeHead) {
            cambersRemaining = cambers.length - readHead;
            cambersRemaining += writeHead;
        } else {
            cambersRemaining = writeHead - readHead;
        }

        return (cambersRemaining * cap) + (cambers[writeHead].getWriteHead() - cambers[readHead].getReadHead());
    }

    @Override
    public int sizeof() {
        return cambers.length * cap;
    }

    private synchronized boolean rotateRead() {
        if (readHead != writeHead) {
            readHead = (readHead + 1) % cambers.length;
            return true;
        }
        return false;
    }

    private synchronized void rotateWrite() {
        writeHead = (writeHead + 1) % cambers.length;
    }

    public int readHead() {
        return readHead;
    }

    public int writeHead() {
        return writeHead;
    }
}
