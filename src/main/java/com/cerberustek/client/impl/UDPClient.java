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

package com.cerberustek.client.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetPipeline;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.server.NetServer;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.client.NetClient;
import com.cerberustek.udp.UDPValve;
import com.cerberustek.udp.UDPPipeline;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;

public class UDPClient implements NetClient {

    private final DatagramSocket socket;
    private final int bufferCapacity;
    private final byte[] received;
    private NetValve valve;
    private String passphrase;
    private WorkerBoss boss;
    private WorkerTask task;
    private String group;
    private String valveGroup;

    public UDPClient(SocketAddress socketAddress, String valveGroup, String passphrase) throws SocketException {
        this(socketAddress, valveGroup, passphrase, NetServer.DEFAULT_BUFFERCAPACITY);
    }

    public UDPClient(SocketAddress socketAddress, String valveGroup, String passphrase, int bufferCapacity) throws SocketException {
        this(new DatagramSocket(), valveGroup, passphrase, bufferCapacity);
        socket.connect(socketAddress);
    }

    public UDPClient(DatagramSocket socket, String valveGroup, String passphrase, int bufferCapacity) {
        this.socket = socket;
        this.passphrase = passphrase;
        this.bufferCapacity = bufferCapacity;
        this.valveGroup = valveGroup;
        this.received = new byte[bufferCapacity + 4];
    }

    @Override
    public NetValve getValve() {
        return valve;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    @Override
    public void start(WorkerBoss boss, String group, WorkerPriority priority) {
        this.boss = boss;
        this.group = group;
        this.task = boss.submitTask(this::update, priority, group, -1);

        NetPipeline pipeline = new UDPPipeline(socket, socket.getLocalSocketAddress(), socket.getRemoteSocketAddress());
        valve = new UDPValve(pipeline, bufferCapacity);

        try {
            valve.start(boss, valveGroup, priority);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            pipeline.write(passphrase.getBytes());
        } catch (IOException e) {
            CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                    new NetDisconnectionEvent(valve, e));
            valve.stop();
        }
    }

    private void update(double deltaT, int i) {
        DatagramPacket packet = new DatagramPacket(received, 0, received.length);
        try {
            socket.receive(packet);
            int length = (received[0] & 0xFF) +
                    ((received[1] & 0xFF) << 8) +
                    ((received[2] & 0xFF) << 16) +
                    ((received[3] & 0xFF) << 24);
            // System.out.println("--- "  + length + " bytes!");
            valve.updateInputs(received, 4, length);
        } catch (IOException e) {
            if (!socket.isClosed())
                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetDisconnectionEvent(valve, e));
            stop();
        }

        // valve.updateOutputs();
    }

    @Override
    public void stop() {
        boss.decomissionTask(task, group);
        socket.close();
        valve.stop();
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public WorkerBoss getBoss() {
        return boss;
    }

    @Override
    public WorkerTask getTask() {
        return task;
    }
}
