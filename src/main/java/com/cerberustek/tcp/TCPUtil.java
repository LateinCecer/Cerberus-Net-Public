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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TCPUtil {

    public static byte[] read(SocketChannel channel, int bufferSize) throws IOException {
        /*ByteBuffer header = ByteBuffer.allocate(4);

        if (readFully(channel, header, 4)) {
            header.rewind();
            int length = header.getInt();
            if (length > NetServer.DEFAULT_TRAFFICCAP) {
                // data chunk to large. Possible DDOS attack.
                throw new BufferOverflowException();
            }

            ByteBuffer buffer = ByteBuffer.allocate(length);

            if (!readFully(channel, buffer, length)) {
                System.err.println("WTF: " + " / " + length);
                return new byte[0];
            }

            byte[] output = new byte[length];
            buffer.rewind();
            buffer.get(output);
            return output;
        }
        return new byte[0];*/

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int i = channel.read(buffer);
        buffer.flip();

        if (i == -1)
            return new byte[0];

        byte[] data = new byte[i];
        buffer.get(data);
        return data;
    }

    public static boolean readFully(SocketChannel channel, ByteBuffer header, int len) throws IOException {
        int read = 0;
        for (int i = channel.read(header); i != -1 && read < len; i = channel.read(header))
            read += i;
        return read == len;
    }
}
