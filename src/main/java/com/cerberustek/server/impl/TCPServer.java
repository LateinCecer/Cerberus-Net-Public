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
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.*;
import com.cerberustek.exceptions.ClientDisconnectExcpetion;
import com.cerberustek.worker.Startable;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.server.NetServer;
import com.cerberustek.tcp.TCPUtil;
import com.cerberustek.tcp.TCPPipeline;
import com.cerberustek.udp.UDPValve;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.channels.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class TCPServer implements NetServer {

    private final HashMap<SocketAddress, NetValve> valves = new HashMap<>();
    private final HashSet<SocketAddress> banned = new HashSet<>();

    private final ServerSocketChannel socket;
    private final int bufferCapacity;
    private final int timeOut;
    private final int backlog;
    private final String connectionGroup;
    private final String timeOutGroup;
    private final Selector selector;

    private String group;
    private WorkerTask task;
    private WorkerBoss boss;
    private boolean shouldStop = false;

    public TCPServer(InetSocketAddress address, String connectionGroup, String timeOutGroup) throws IOException {
        this(ServerSocketChannel.open(), address, connectionGroup, timeOutGroup);
    }

    public TCPServer(ServerSocketChannel socket, InetSocketAddress address, String connectionGroup, String timeOutGroup)
            throws IOException {
        this(socket, address, connectionGroup, timeOutGroup, NetServer.DEFAULT_BACKLOG, NetServer.DEFAULT_TIMEOUT,
                DEFAULT_BUFFERCAPACITY);
    }

    public TCPServer(ServerSocketChannel socket, InetSocketAddress address, String connectionGroup, String timeOutGroup,
                     int backlog, int timeOut, int bufferCapacity) throws IOException {
        this.socket = socket;
        this.connectionGroup = connectionGroup;
        this.timeOutGroup = timeOutGroup;
        this.backlog = backlog;
        this.timeOut = timeOut;
        this.bufferCapacity = bufferCapacity;
        this.selector = Selector.open();

        socket.configureBlocking(false);
        socket.socket().bind(address);
        socket.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public SocketAddress getLocalAddress() {
        return socket.socket().getLocalSocketAddress();
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

    @SuppressWarnings("Duplicates")
    private void update(double time, int rep) {
        try {
            this.selector.select();
            Iterator<SelectionKey> tr = selector.selectedKeys().iterator();

            while (tr.hasNext()) {
                SelectionKey key = tr.next();
                tr.remove();

                if (key.isValid())
                    process(key);
            }
        } catch (Exception e) {
            if (shouldStop) {
                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetServerCloseEvent(this, e));
                stop();
            }
        }
    }

    private void process(SelectionKey key) throws IOException, InterruptedException {
        if (key.isAcceptable() && valves.size() < backlog) {
            accept();
        } else if (key.isReadable()) {
            SocketAddress address = ((SocketChannel) key.channel()).getRemoteAddress();

            if (!banned.contains(address)) {
                NetValve valve = valves.get(address);

                if (valve != null) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    try {
                        byte[] raw = TCPUtil.read(channel, bufferCapacity);
                        try {
                            valve.updateInputs(raw, 0, raw.length);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            CerberusRegistry.getInstance().getService(CerberusEvent.class)
                                    .executeFullEIF(new NetDisconnectionEvent(valve, new ClientDisconnectExcpetion(valve)));
                        }
                    } catch (BufferOverflowException e) {
                        e.printStackTrace();
                    }
                } else {
                    SocketChannel channel = (SocketChannel) key.channel();
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (channel) {
                        channel.notifyAll();
                        channel.wait();
                    }
                }
            }
        }
    }

    private void accept() {
        try {
            final SocketChannel clientChannel = socket.accept();
            final SocketAddress remoteAddress = clientChannel.getRemoteAddress();

            if (!banned.contains(remoteAddress)) {
                clientChannel.configureBlocking(false);
                clientChannel.register(this.selector, SelectionKey.OP_READ);

                final WorkerTask t1 = boss.submitTask((d) -> {
                    try {
                        if (CerberusRegistry.getInstance().getService(CerberusEvent.class).executeShortEIF(
                                new NetPreConnectionEvent(remoteAddress))) {

                            synchronized (clientChannel) {
                                clientChannel.wait(timeOut);
                            }
                            byte[] raw = TCPUtil.read(clientChannel, bufferCapacity);
                            if (raw.length == 0)
                                throw new IOException("Took to long to connect!");
                            String passphrase = new String(raw);

                            if (CerberusRegistry.getInstance().getService(CerberusEvent.class).executeShortEIF(
                                    new NetConnectionEvent(remoteAddress,
                                            clientChannel.getLocalAddress(), passphrase))) {

                                NetValve valve = new UDPValve(new TCPPipeline(
                                        clientChannel), bufferCapacity);
                                valves.put(remoteAddress, valve);
                                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                                        new NetPostConnectionEvent(valve));
                            }
                        }
                    } catch (IOException | InterruptedException | ClosedSelectorException e) {
                        CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                                new NetFailedConnectionEvent(remoteAddress, e));
                        try {
                            clientChannel.close();
                            NetValve valve = valves.get(remoteAddress);
                            if (valve != null)
                                valve.stop();
                            valves.remove(remoteAddress);
                        } catch (IOException e1) {
                            // Ignore
                        }
                    } finally {
                        synchronized (clientChannel) {
                            clientChannel.notifyAll();
                        }
                    }
                }, WorkerPriority.LOW, connectionGroup);

                boss.submitTask((d) -> boss.decomissionTask(t1, connectionGroup), WorkerPriority.MEDIUM, timeOutGroup,
                        timeOut);
            }
        } catch (IOException e) {
            if (!shouldStop)
                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetServerCloseEvent(this, e));
            stop();
        }
    }

    @Override
    public void stop() {
        if (!shouldStop) {
            boss.decomissionTask(task, group);
            valves.values().forEach(Startable::stop);
            valves.clear();

            try {
                selector.close();
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            shouldStop = true;
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
