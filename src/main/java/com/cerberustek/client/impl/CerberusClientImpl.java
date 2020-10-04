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
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.event.impl.CerberusEventImpl;
import com.cerberustek.events.NetClientConnectionEvent;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.events.NetFailedConnectionEvent;
import com.cerberustek.server.NetServer;
import com.cerberustek.worker.Worker;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerStatus;
import com.cerberustek.worker.impl.WorkerBossImpl;
import com.cerberustek.ConnectionType;
import com.cerberustek.client.CerberusClient;
import com.cerberustek.client.NetClient;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

@EventHandler(events = {NetDisconnectionEvent.class, NetClientConnectionEvent.class, NetFailedConnectionEvent.class})
public class CerberusClientImpl implements CerberusClient, EventListener {

    public final String GROUP_MAIN = "client_main" + super.toString();
    public final String GROUP_CRAWLER = "client_crawler" + super.toString();
    public final String GROUP_HANDSHAKE = "client_handshake" + super.toString();
    public static final short HANDSHAKE_CHANNEL = (short) 1;

    private final ConnectionType connectionType;
    private final String passphrase;
    private final int bufferCap;

    private ClientHandshakeHandler handshakeHandler;
    private NetClient client;
    private WorkerBoss boss;
    private Worker crawlerWorker;
    private Worker mainWorker;
    private Worker handshakeWorker;
    private int timeOut;
    private int handShake;

    public CerberusClientImpl(ConnectionType connectionType, WorkerBoss boss, String passphrase,
                              int bufferCap) {
        this.connectionType = connectionType;
        this.boss = boss;
        this.passphrase = passphrase;
        this.bufferCap = bufferCap;
        this.handShake = NetServer.DEFAULT_HANDSHAKE_INTERVAL;
        this.timeOut = handShake * 10;

        handshakeHandler = new ClientHandshakeHandler(this, HANDSHAKE_CHANNEL);
    }

    public CerberusClientImpl(ConnectionType connectionType, WorkerBoss boss, String passphrase) {
        this(connectionType, boss, passphrase, NetServer.DEFAULT_BUFFERCAPACITY);
    }

    public CerberusClientImpl(ConnectionType connectionType, String passphrase) {
        this(connectionType, new WorkerBossImpl(), passphrase);
    }

    @Override
    public void connect(InetSocketAddress host) throws IOException {
        CerberusRegistry registry = CerberusRegistry.getInstance();
        if (registry.getService(CerberusEvent.class) == null) {
            registry.registerService(new CerberusEventImpl());
            registry.requestStart(CerberusEvent.class);
        }
        CerberusEvent cerberusEvent = registry.getService(CerberusEvent.class);

        boss.createGroup(GROUP_MAIN, WorkerPriority.HIGH);
        boss.createGroup(GROUP_HANDSHAKE, WorkerPriority.MEDIUM);
        boss.createGroup(GROUP_CRAWLER, WorkerPriority.MEDIUM);
        mainWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_MAIN);
        handshakeWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_HANDSHAKE);
        crawlerWorker = boss.createWorker(WorkerPriority.MEDIUM, GROUP_CRAWLER, GROUP_HANDSHAKE);

        if (connectionType == ConnectionType.TCP) {
            client = new TCPClient(SocketChannel.open(), host, GROUP_CRAWLER,
                    passphrase, bufferCap, timeOut);
        } else {
            DatagramSocket socket = new DatagramSocket();
            client = new UDPClient(socket,
                    GROUP_CRAWLER, passphrase, bufferCap);
            socket.connect(host);
            cerberusEvent.executeFullEIF(new NetClientConnectionEvent(client));
        }

        cerberusEvent.addListener(this);
        cerberusEvent.addListener(handshakeHandler);

        handshakeHandler.start(boss, GROUP_HANDSHAKE, WorkerPriority.MEDIUM);

        try {
            client.start(boss, GROUP_MAIN, WorkerPriority.MEDIUM);

            mainWorker.changeStatus(WorkerStatus.STARTING);
            handshakeWorker.changeStatus(WorkerStatus.STARTING);
            crawlerWorker.changeStatus(WorkerStatus.STARTING);
        } catch (IOException e) {
            client = null;
            handshakeHandler.stop();

            cerberusEvent.removeListener(this);
            cerberusEvent.removeListener(handshakeHandler);

            boss.decomissionGroup(GROUP_MAIN);
            boss.decomissionGroup(GROUP_HANDSHAKE);
            boss.decomissionGroup(GROUP_CRAWLER);
            boss.decomissionWorker(mainWorker);
            boss.decomissionWorker(handshakeWorker);
            boss.decomissionWorker(crawlerWorker);
            throw e;
        }
    }

    @Override
    public void disconnect() {
        /*
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace)
            System.err.println("# " + element.toString());
        System.err.println("##############################################################");*/

        CerberusEvent event = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        event.removeListener(this);
        event.removeListener(handshakeHandler);

        handshakeHandler.stop();
        if (client != null)
            client.stop();

        mainWorker.changeStatus(WorkerStatus.TERMINATING);
        crawlerWorker.changeStatus(WorkerStatus.TERMINATING);
        handshakeWorker.changeStatus(WorkerStatus.TERMINATING);

        boss.decomissionWorker(mainWorker);
        boss.decomissionWorker(crawlerWorker);
        boss.decomissionWorker(handshakeWorker);
        boss.decomissionGroup(GROUP_MAIN);
        boss.decomissionGroup(GROUP_CRAWLER);
        boss.decomissionGroup(GROUP_HANDSHAKE);
        client = null;
    }

    @Override
    public int getTimeOutDelay() {
        return timeOut;
    }

    @Override
    public void setTimeOutDelay(int timeOutDelay) {
        this.timeOut = timeOutDelay;
    }

    @Override
    public int getHandshakeInterval() {
        return handShake;
    }

    @Override
    public void setHandshakeInterval(int handshakeInterval) {
        this.handShake = handshakeInterval;

        handshakeHandler.stop();
        handshakeHandler.start(boss, GROUP_HANDSHAKE, WorkerPriority.MEDIUM);
    }

    @Override
    public int getPing() {
        return handshakeHandler.getPing();
    }

    @Override
    public boolean isRunning() {
        return client != null;
    }

    @Override
    public ConnectionType getConnectionType() {
        return connectionType;
    }

    @Override
    public WorkerBoss getWorkerBoss() {
        return boss;
    }

    @Override
    public NetClient getNetClient() {
        return client;
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetDisconnectionEvent) {
            System.err.println("Lost connection to server! Cause: " + ((NetDisconnectionEvent) event).getE());
            // ((NetDisconnectionEvent) event).getE().printStackTrace();
            System.err.println("Own socket: " + getNetClient().getLocalAddress());
            try {
                disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (event instanceof NetClientConnectionEvent) {
            System.out.println("Client fully connected to the server!");
            System.out.println("Local address: " + client.getLocalAddress());
        } else if (event instanceof NetFailedConnectionEvent) {
            System.err.println("Error: " + event);
        }
        return true;
    }
}
