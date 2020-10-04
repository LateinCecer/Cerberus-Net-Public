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

package com.cerberustek.tcp;

import com.cerberustek.channel.NetPipeline;
import com.cerberustek.ConnectionType;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TCPPipeline implements NetPipeline {

    private final SocketChannel channel;

    public TCPPipeline(SocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public synchronized void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public synchronized void write(byte[] data, int offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length + 4);
        buffer.putInt(length);
        buffer.put(data, offset, length);
        buffer.rewind();
        channel.write(buffer);
    }

    @Override
    public boolean isClosed() {
        return !channel.isOpen();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.socket().getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.socket().getLocalSocketAddress();
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.TCP;
    }
}
