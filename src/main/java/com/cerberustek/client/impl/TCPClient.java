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
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetClientConnectionEvent;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.server.NetServer;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.client.NetClient;
import com.cerberustek.tcp.TCPUtil;
import com.cerberustek.tcp.TCPPipeline;
import com.cerberustek.udp.UDPValve;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class TCPClient implements NetClient {

    private final int timeOut;
    private final int bufferCapacity;
    private final Selector selector;

    private SocketChannel socket;
    private NetValve valve;
    private String passphrase;

    private WorkerBoss boss;
    private WorkerTask task;
    private String group;
    private String valveGroup;

    public TCPClient(InetSocketAddress address, String valveGroup, String passphrase, int timeout)
            throws IOException {
        this(SocketChannel.open(), address, valveGroup, passphrase, timeout);
    }

    public TCPClient(SocketChannel socket, InetSocketAddress address, String valveGroup, String passphrase, int timeout)
            throws IOException {
        this(socket, address, valveGroup, passphrase, NetServer.DEFAULT_BUFFERCAPACITY, timeout);
    }

    public TCPClient(SocketChannel socket, InetSocketAddress address, String valveGroup, String passphrase,
                     int bufferCapacity, int timeout) throws IOException {
        this.valveGroup = valveGroup;
        this.bufferCapacity = bufferCapacity;
        this.timeOut = timeout;
        this.passphrase = passphrase;
        this.socket = socket;
        this.selector = Selector.open();

        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_CONNECT);
        socket.connect(address);
    }

    @Override
    public NetValve getValve() {
        return valve;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return socket.socket().getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return socket.socket().getLocalSocketAddress();
    }

    @Override
    public void start(WorkerBoss boss, String group, WorkerPriority priority) throws IOException {
        int waitCounter = 0;
        while (socket.isConnectionPending()) {
            update();

            if (waitCounter > timeOut) {
                socket.close();
                throw new IOException("Connection timeout");
            }

            try {
                // There is no smarter, equally good way to do this
                //noinspection BusyWait
                Thread.sleep(100);
                waitCounter += 100;
            } catch (InterruptedException ignore) {}
        }

        if (!socket.isConnected())
            throw new IOException("Could not connect to host");

        this.boss = boss;
        this.group = group;
        this.task = boss.submitTask(this::update, priority, group, -1);
        valve = new UDPValve(new TCPPipeline(socket), bufferCapacity);
        // valve.start(boss, valveGroup, priority);
    }

    private void update() throws IOException {
        this.selector.select();
        Iterator<SelectionKey> tr = selector.selectedKeys().iterator();

        while (tr.hasNext()) {
            SelectionKey key = tr.next();
            tr.remove();

            if (key.isValid())
                process(key);
        }
    }

    @SuppressWarnings("Duplicates")
    private void update(double time, int rep) {
        try {
            update();
        } catch (IOException | ClosedSelectorException | ArrayIndexOutOfBoundsException e) {
            if (selector.isOpen())
                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetDisconnectionEvent(valve, e));
            stop();
        }
    }

    private void process(SelectionKey key) throws IOException {
        if (key.isConnectable()) {

            SocketChannel client = (SocketChannel) key.channel();
            if (client != null && client.finishConnect()) {
                client.register(this.selector, SelectionKey.OP_READ);
                ByteBuffer buffer = ByteBuffer.allocate(passphrase.length() + 4);
                buffer.putInt(passphrase.length());
                buffer.put(passphrase.getBytes());
                buffer.rewind();
                socket.write(buffer);

                CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                        new NetClientConnectionEvent(this));
            }

        } else if (key.isReadable()) {

            SocketChannel channel = (SocketChannel) key.channel();
            byte[] raw = TCPUtil.read(channel, bufferCapacity);
            valve.updateInputs(raw, 0, raw.length);
        }
    }

    @Override
    public void stop() {
        boss.decomissionTask(task, group);

        try {
            if (socket != null && socket.socket() != null)
                socket.socket().close();
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (selector != null)
                selector.close();
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            // Ignore
        }
        // valve.stop();
        try {
            if (valve != null && valve.getPipeline() != null)
                valve.getPipeline().close();
        } catch (IOException e) {
            // ignore
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
