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

package com.cerberustek.udp;

import com.cerberustek.channel.NetPipeline;
import com.cerberustek.ConnectionType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;

public class UDPPipeline implements NetPipeline {

    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private final DatagramSocket socket;

    private boolean closed;

    public UDPPipeline(DatagramSocket socket, SocketAddress localAddress, SocketAddress remoteAddress) {
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.socket = socket;

        closed = false;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public synchronized void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public synchronized void write(byte[] data, int offset, int length) throws IOException {
        byte[] buffer = new byte[length + 4];
        buffer[0] = (byte) length;
        buffer[1] = (byte) (length >>> 8);
        buffer[2] = (byte) (length >>> 16);
        buffer[3] = (byte) (length >>> 24);

        System.arraycopy(data, 0, buffer, 4, length);
        DatagramPacket packet = new DatagramPacket(buffer, offset, buffer.length, remoteAddress);
        socket.send(packet);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.UDP;
    }
}
