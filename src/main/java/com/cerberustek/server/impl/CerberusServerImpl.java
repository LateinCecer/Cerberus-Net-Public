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
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.event.impl.CerberusEventImpl;
import com.cerberustek.events.*;
import com.cerberustek.worker.Worker;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.impl.WorkerBossImpl;
import com.cerberustek.ConnectionType;
import com.cerberustek.server.CerberusServer;
import com.cerberustek.server.NetServer;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

@EventHandler(events = {NetPostConnectionEvent.class, NetPreConnectionEvent.class, NetConnectionEvent.class,
        NetDisconnectionEvent.class, NetFailedConnectionEvent.class})
public class CerberusServerImpl implements CerberusServer, EventListener {

    public final String GROUP_CRAWL = "server_crawler" + super.toString();
    public final String GROUP_MAIN = "server_main" + super.toString();
    public final String GROUP_TIMEOUTS = "server_timeouts" + super.toString();
    public final String GROUP_CONNECTIONS = "server_connections" + super.toString();
    public final String GROUP_HANDSHAKE = "server_handshake" + super.toString();
    public static final short HANDSHAKE_CHANNEL = (short) 1;

    private final ConnectionType type;
    private final int port;
    private final int backlog;
    private final int bufferCap;
    private final int connectionTimeOut;
    private final WorkerBoss boss;
    private final ServerHandshakeHandler handshakeHandler;

    private NetServer server;
    private Worker[] crawler;
    private Worker[] channelUpdate;
    private Worker[] handshaker;
    private Worker mainWorker;
    private Worker timeOutWorker;
    private Worker connectionWorker;
    private int handShakeInterval;
    private int timeout;

    public CerberusServerImpl(ConnectionType type, WorkerBoss boss, int port, int backlog,
                              int bufferCap, int connectionTimeOut, int poolSize) {
        this.type = type;
        this.handshakeHandler = new ServerHandshakeHandler(HANDSHAKE_CHANNEL, this);
        this.boss = boss;
        this.crawler = new Worker[poolSize];
        this.channelUpdate = new Worker[poolSize];
        this.handshaker = new Worker[poolSize];

        this.port = port;
        this.backlog = backlog;
        this.bufferCap = bufferCap;
        this.connectionTimeOut = connectionTimeOut;
        this.handShakeInterval = NetServer.DEFAULT_HANDSHAKE_INTERVAL;
        this.timeout = handShakeInterval * 10;
    }

    public CerberusServerImpl(ConnectionType type, WorkerBoss boss, int port, int backlog, int poolSize) {
        this(type, boss, port, backlog, NetServer.DEFAULT_BUFFERCAPACITY,
                NetServer.DEFAULT_TIMEOUT, poolSize);
    }

    public CerberusServerImpl(ConnectionType type, int port, int backlog, int poolSize) {
        this(type, new WorkerBossImpl(), port, backlog, poolSize);
    }

    public CerberusServerImpl(ConnectionType type, int port, int backlog) {
        this(type, port, backlog, NetServer.DEFAULT_POOLSIZE);
    }

    public CerberusServerImpl(ConnectionType type, int port) {
        this(type, port, NetServer.DEFAULT_BACKLOG);
    }

    @Override
    public void start() throws IOException {
        CerberusRegistry registry = CerberusRegistry.getInstance();
        if (registry.getService(CerberusEvent.class) == null) {
            registry.registerService(new CerberusEventImpl());
            registry.requestStart(CerberusEvent.class);
        }
        System.out.println("Starting " + getConnectionType() + " server on local ip with port: " + port + "...");

        if (type == ConnectionType.TCP)
            this.server = new TCPServer(ServerSocketChannel.open(), new InetSocketAddress(port), GROUP_CONNECTIONS,
                    GROUP_TIMEOUTS, backlog, connectionTimeOut, bufferCap);
        else
            this.server = new UDPServer(new DatagramSocket(port), GROUP_CONNECTIONS, GROUP_TIMEOUTS,
                    backlog, connectionTimeOut, bufferCap);

        CerberusEvent cerberusEvent = registry.getService(CerberusEvent.class);
        cerberusEvent.addListener(this);
        cerberusEvent.addListener(handshakeHandler);
        boss.createGroup(GROUP_CRAWL, WorkerPriority.HIGH);
        boss.createGroup(GROUP_MAIN, WorkerPriority.HIGH);
        boss.createGroup(GROUP_CONNECTIONS, WorkerPriority.MEDIUM);
        boss.createGroup(GROUP_TIMEOUTS, WorkerPriority.MEDIUM);
        boss.createGroup(GROUP_HANDSHAKE, WorkerPriority.MEDIUM);
        boss.createGroup("channel_update", WorkerPriority.MEDIUM);

        for (int i = 0; i < crawler.length; i++) {
            crawler[i] = boss.createWorker(WorkerPriority.MEDIUM, GROUP_CRAWL);
//            channelUpdate[i] = boss.createWorker(WorkerPriority.MEDIUM, "channel_update");
            handshaker[i] = boss.createWorker(WorkerPriority.MEDIUM, GROUP_HANDSHAKE);
        }
        mainWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_MAIN);
        timeOutWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_TIMEOUTS);
        connectionWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_CONNECTIONS);

        server.start(boss, GROUP_MAIN, WorkerPriority.HIGH);

        handshakeHandler.start(boss, GROUP_HANDSHAKE, WorkerPriority.HIGH);
    }

    @Override
    public void stop() {
        CerberusEvent event = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        event.removeListener(this);
        event.removeListener(handshakeHandler);

        handshakeHandler.stop();
        server.stop();
        boss.decomissionWorker(mainWorker);
        boss.decomissionWorker(timeOutWorker);
        boss.decomissionWorker(connectionWorker);

        for (Worker worker : crawler)
            boss.decomissionWorker(worker);
        for (Worker worker : channelUpdate)
            boss.decomissionWorker(worker);
        for (Worker worker : handshaker)
            boss.decomissionWorker(worker);

        boss.decomissionGroup(GROUP_CRAWL);
        boss.decomissionGroup(GROUP_MAIN);
        boss.decomissionGroup(GROUP_CONNECTIONS);
        boss.decomissionGroup(GROUP_TIMEOUTS);
        boss.decomissionGroup(GROUP_HANDSHAKE);
        server = null;
    }

    @Override
    public int getHandshakeInterval() {
        return handShakeInterval;
    }

    @Override
    public void setHandshakeInterval(int handshakeInterval) {
        this.handShakeInterval = handshakeInterval;

        handshakeHandler.stop();
        handshakeHandler.start(boss, GROUP_HANDSHAKE, WorkerPriority.LOW);
    }

    @Override
    public int getTimeOutDelay() {
        return timeout;
    }

    @Override
    public void setTimeOutDelay(int timeOutDelay) {
        this.timeout = timeOutDelay;
    }

    @Override
    public int getPing(NetValve valve) {
        return handshakeHandler.getPing(valve);
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    @Override
    public WorkerBoss getWorkerBoss() {
        return boss;
    }

    @Override
    public ConnectionType getConnectionType() {
        return type;
    }

    @Override
    public NetServer getNetServer() {
        return server;
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetPostConnectionEvent) {
            System.out.println("Client connected to server!");
            try {
                ((NetPostConnectionEvent) event).getValve().start(boss, GROUP_CRAWL, WorkerPriority.MEDIUM);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (event instanceof NetPreConnectionEvent) {
            System.out.println("Client with ip: " + ((NetPreConnectionEvent) event).getAddress() + " tries to" +
                    " connect to the server!");
        } else if (event instanceof NetConnectionEvent) {
            System.out.println("Client send connection passphrase!");
        } else if (event instanceof NetDisconnectionEvent) {
            System.out.println("Client with ip: " + ((NetDisconnectionEvent) event).getValve().getPipeline().
                    getRemoteAddress() + " lost connection! Cause: " + ((NetDisconnectionEvent) event).getE());
            server.timeOutValve(((NetDisconnectionEvent) event).getValve().getPipeline().getRemoteAddress());
        } else if (event instanceof NetFailedConnectionEvent) {
            System.out.println("Client with ip: " + ((NetFailedConnectionEvent) event).getRemoteAddress() +
                    " failed to connect to the server! Cause: " + ((NetFailedConnectionEvent) event).getException());
        }
        return true;
    }
}
