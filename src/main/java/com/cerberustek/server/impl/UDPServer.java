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

package com.cerberustek.server.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetPipeline;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.*;
import com.cerberustek.worker.Startable;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.server.NetServer;
import com.cerberustek.udp.UDPValve;
import com.cerberustek.udp.UDPPipeline;

import java.io.IOException;
import java.net.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class UDPServer implements NetServer, Startable {

    private final HashMap<SocketAddress, NetValve> valves = new HashMap<>();
    private final HashSet<SocketAddress> banned = new HashSet<>();

    private final DatagramSocket socket;
    private final int bufferCapacity;
    private final int timeOut;
    private final int backlog;

    private String group;
    private String connectionGroup;
    private String timeOutGroup;
    private WorkerTask task;
    private WorkerBoss boss;
    private byte[] received;
    boolean shouldClose = false;

    public UDPServer(int port, String connectionGroup, String timeOutGroup) throws SocketException {
        this(new DatagramSocket(port), connectionGroup, timeOutGroup);
    }

    public UDPServer(DatagramSocket socket, String connectionGroup, String timeOutGroup) {
        this(socket, connectionGroup, timeOutGroup, NetServer.DEFAULT_BACKLOG, NetServer.DEFAULT_TIMEOUT,
                NetServer.DEFAULT_BUFFERCAPACITY);
    }

    public UDPServer(DatagramSocket socket, String connectionGroup, String timeOutGroup, int backlog, int timeOut, int bufferCapacity) {
        this.socket = socket;
        this.connectionGroup = connectionGroup;
        this.timeOutGroup = timeOutGroup;
        this.timeOut = timeOut;
        this.backlog = backlog;
        this.bufferCapacity = bufferCapacity;
        this.received = new byte[this.bufferCapacity + 4];
    }

    @Override
    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    @Override
    public void timeOutValve(SocketAddress remoteAddress) {
        NetValve valve = getValve(remoteAddress);
        if (valve != null) {
            valve.stop();
            valves.remove(remoteAddress);
        }
    }

    @Override
    public NetValve getValve(SocketAddress remoteAddress) {
        return valves.get(remoteAddress);
    }

    @Override
    public Collection<NetValve> getValves() {
        return valves.values();
    }

    @Override
    public void ban(SocketAddress remoteAddress) {
        if (!banned.contains(remoteAddress) && CerberusRegistry.getInstance().getService(CerberusEvent.class)
                .executeFullEIF(new NetServerBanEvent(remoteAddress, 0)))
            banned.add(remoteAddress);
    }

    @Override
    public void ban(SocketAddress remoteAddress, int time) {
        if (!banned.contains(remoteAddress) && CerberusRegistry.getInstance().getService(CerberusEvent.class)
                .executeFullEIF(new NetServerBanEvent(remoteAddress, time))) {

            banned.add(remoteAddress);
            pardon(remoteAddress, time);
        }
    }

    @Override
    public void pardon(SocketAddress remoteAddress) {
        if (CerberusRegistry.getInstance().getService(CerberusEvent.class)
                .executeFullEIF(new NetServerPardonEvent(remoteAddress)))
            banned.remove(remoteAddress);
    }

    @Override
    public void pardon(SocketAddress remoteAddress, int delay) {
        boss.submitTask((t) -> pardon(remoteAddress), timeOutGroup, delay);
    }

    @Override
    public void start(WorkerBoss boss, String group, WorkerPriority priority) {
        this.boss = boss;
        this.group = group;
        this.task = boss.submitTask(this::update, priority, group, -1);
    }

    private void update(double deltaT, int i) {
        try {
            DatagramPacket packet = new DatagramPacket(received, received.length);
            socket.receive(packet);
            SocketAddress remoteAddress = packet.getSocketAddress();

            if (!banned.contains(remoteAddress)) {
                NetValve valve = valves.get(packet.getSocketAddress());
                final int length = (received[0] & 0xFF) +
                        ((received[1] & 0xFF) << 8) +
                        ((received[2] & 0xFF) << 16) +
                        ((received[3] & 0xFF) << 24);

                // CerberusRegistry.getInstance().debug("received: " + length + " bytes!");

                if (valve != null) {
                    valve.updateInputs(received, 4, length);
                } else if (valves.size() < backlog) {
                    final byte[] data = new byte[length];
                    System.arraycopy(received, 4, data, 0, length);

                    final WorkerTask task = boss.submitTask((d) -> {
                        if (CerberusRegistry.getInstance().getService(CerberusEvent.class).executeShortEIF(
                                new NetPreConnectionEvent(packet.getSocketAddress()))) {

                            if (CerberusRegistry.getInstance().getService(CerberusEvent.class).executeShortEIF(
                                    new NetConnectionEvent(packet.getSocketAddress(),
                                            socket.getLocalSocketAddress(), new String(data)))) {

                                NetPipeline pipe = new UDPPipeline(socket, socket.getLocalSocketAddress(),
                                        packet.getSocketAddress());
                                NetValve v = new UDPValve(pipe,
                                        bufferCapacity);
                                valves.put(packet.getSocketAddress(), v);
                                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                                        new NetPostConnectionEvent(v));
                            }
                        }
                    }, WorkerPriority.LOW, connectionGroup);
                    boss.submitTask((b) -> boss.decomissionTask(task, connectionGroup), WorkerPriority.MEDIUM, timeOutGroup,
                            timeOut);
                }
            }
        } catch (IOException e) {
            if (!shouldClose)
                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetServerCloseEvent(this, e));
            stop();
        }

        /*try {
            valves.values().forEach(NetValve::updateOutputs);
        } catch (ConcurrentModificationException e) {
            // Ignore!
        }*/
    }

    @Override
    public void stop() {
        if (!shouldClose) {
            boss.decomissionTask(task, group);
            valves.values().forEach(Startable::stop);
            valves.clear();
            socket.close();
        }
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
