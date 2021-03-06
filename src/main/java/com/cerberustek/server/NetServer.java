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

package com.cerberustek.server;

import com.cerberustek.channel.NetValve;
import com.cerberustek.worker.Startable;

import java.net.SocketAddress;
import java.util.Collection;

public interface NetServer extends Startable {

    int DEFAULT_TIMEOUT = 100;
    int DEFAULT_BUFFERCAPACITY = 24 * 1024;
    int DEFAULT_TRAFFICCAP = 64 * 1024;
    int DEFAULT_BACKLOG = 50;
    int DEFAULT_HANDSHAKE_INTERVAL = 200;
    int DEFAULT_POOLSIZE = 1;

    SocketAddress getLocalAddress();

    void timeOutValve(SocketAddress remoteAddress);
    NetValve getValve(SocketAddress remoteAddress);
    Collection<NetValve> getValves();

    void ban(SocketAddress remoteAddress);
    void ban(SocketAddress remoteAddress, int time);
    void pardon(SocketAddress remoteAddress);
    void pardon(SocketAddress remoteAddress, int delay);
}
