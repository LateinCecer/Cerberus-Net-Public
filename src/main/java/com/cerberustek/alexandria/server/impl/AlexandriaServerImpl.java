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

package com.cerberustek.alexandria.server.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.alexandria.collection.Batch;
import com.cerberustek.alexandria.collection.BatchInfo;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.events.ExceptionEvent;
import com.cerberustek.exceptions.UnknownBatchException;
import com.cerberustek.service.CerberusService;
import com.cerberustek.settings.Settings;
import com.cerberustek.settings.impl.SettingsImpl;
import com.cerberustek.CerberusNet;
import com.cerberustek.ConnectionType;
import com.cerberustek.alexandria.Alexandria;
import com.cerberustek.alexandria.server.AlexandriaServer;
import com.cerberustek.server.CerberusServer;
import com.cerberustek.utils.DiscriminatorFile;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerStatus;
import com.cerberustek.worker.impl.WorkerBossImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;

public class AlexandriaServerImpl implements AlexandriaServer {

    private static final String BD_SERVICE_GROUP = "db_service";

    private final HashMap<String, Batch> batches = new HashMap<>();
    private final WorkerBoss workerBoss;
    private final Settings settings;

    private CerberusServer tcpServer;
    private CerberusServer udpServer;
    private AlexandriaNetworkServer networkServer;

    public AlexandriaServerImpl() {
        workerBoss = new WorkerBossImpl();
        settings = new SettingsImpl(new File("alexandria_server.xml"));
    }

    @Override
    public boolean hasTCP() {
        return tcpServer != null && tcpServer.isRunning();
    }

    @Override
    public boolean hasUDP() {
        return udpServer != null && udpServer.isRunning();
    }

    @Override
    public WorkerBoss getWorkerBoss() {
        return workerBoss;
    }

    @Override
    public Batch openBatch(String name, String password) throws FileNotFoundException {
        BatchInfo info = BatchInfo.readBatchInfo(name);
        if (info == null) {
            CerberusRegistry.getInstance().debug("Batch info does not exist for batch " +
                     name + ".");

            UnknownBatchException exception = new UnknownBatchException(name);
            CerberusRegistry.getInstance().getService(CerberusEvent.class)
                    .executeFullEIF(new ExceptionEvent(AlexandriaServer.class,
                            exception));
            throw exception;
        }
        DiscriminatorFile discriminatorFile = info.getDiscriminatorFile();
        if (discriminatorFile == null) {
            CerberusRegistry.getInstance().warning("Unable to load discriminator file for" +
                    " batch " + name + ".");
            return null;
        }

        DiscriminatorMap map;
        try {
            map = discriminatorFile.read();
        } catch (FileNotFoundException e) {
            CerberusRegistry.getInstance().getService(CerberusEvent.class)
                    .executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
            throw e;
        }

        Batch batch = batches.get(name);
        if (batch == null) {
            try {
                batch = info.loadBatch();
            } catch (NoSuchMethodException | IllegalAccessException |
                    InvocationTargetException | InstantiationException e) {

                CerberusRegistry.getInstance().getService(CerberusEvent.class)
                        .executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
                CerberusRegistry.getInstance().warning("Unable to open batch " + name);
                return null;
            }
            CerberusRegistry.getInstance().debug("Initialized batch " + name);
        }

        try {
            if (!batch.open(password, map)) {
                CerberusRegistry.getInstance().warning("Unable to open batch " + name + "!");
                return null;
            }
        } catch (IllegalAccessException e) {
            CerberusRegistry.getInstance().getService(CerberusEvent.class)
                    .executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
            return null;
        }
        CerberusRegistry.getInstance().debug("Opened batch " + name);
        return batch;
    }

    @Override
    public void closeBatch(String name) {
        Batch batch = batches.get(name);
        if (batch == null)
            return;

        batch.close();
        batches.remove(name);
    }

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void start() {
        long startTime = System.currentTimeMillis();
        CerberusRegistry.getInstance().info("Starting AlexandriaDB...");

        settings.init();
        if (settings.getBoolean("tcp", true)) {
            tcpServer = CerberusNet.createServer(ConnectionType.TCP, workerBoss,
                    settings.getInteger("tcp_port", Alexandria.DEFAULT_TCP_PORT),
                    settings.getInteger("tcp_backlog", 200),
                    settings.getInteger("tcp_buffercap", 4096),
                    settings.getInteger("tcp_connectiontimeout", 2000),
                    settings.getInteger("tcp_poolsize", 1));

            try {
                tcpServer.start();
                CerberusRegistry.getInstance().info("Started TCP server");
            } catch (IOException e) {
                CerberusRegistry.getInstance().critical("Could not start TCP server!");
            }
        }

        if (settings.getBoolean("udp", false)) {
            udpServer = CerberusNet.createServer(ConnectionType.UDP, workerBoss,
                    settings.getInteger("udp_port", Alexandria.DEFAULT_UDP_PORT),
                    settings.getInteger("udp_backlog", 200),
                    settings.getInteger("udp_buffercap", 4096),
                    settings.getInteger("udp_connectiontimeout", 2000),
                    settings.getInteger("udp_poolsize", 1));

            try {
                udpServer.start();
                CerberusRegistry.getInstance().info("Started UDP server");
            } catch (IOException e) {
                CerberusRegistry.getInstance().critical("Could not start UDP server!");
            }
        }

        workerBoss.createGroup(BD_SERVICE_GROUP, WorkerPriority.MEDIUM);
        for (int i = 0; i < settings.getInteger("db_pool", 1); i++)
            workerBoss.createWorker(WorkerPriority.MEDIUM, BD_SERVICE_GROUP);
        workerBoss.changeStatus(WorkerStatus.STARTING);

        if (tcpServer != null) {
            tcpServer.setTimeOutDelay(settings.getInteger("tcp_timeout", 10000));
            tcpServer.setHandshakeInterval(settings.getInteger("tcp_handshake", 5000));
        }

        if (udpServer != null) {
            udpServer.setTimeOutDelay(settings.getInteger("udp_timeout", 10000));
            udpServer.setHandshakeInterval(settings.getInteger("udp_handshake", 5000));
        }

        networkServer = new AlexandriaNetworkServer();
        CerberusRegistry.getInstance().getService(CerberusEvent.class).addListener(networkServer);
        CerberusRegistry.getInstance().info("AlexandriaDB is up and running! (Done in "
                + (System.currentTimeMillis() - startTime) + "ms)");
    }

    public CerberusServer getTCPServer() {
        return tcpServer;
    }

    public CerberusServer getUDPServer() {
        return udpServer;
    }

    @Override
    public void stop() {
        CerberusRegistry.getInstance().getService(CerberusEvent.class).removeListener(networkServer);
        networkServer = null;

        if (hasTCP())
            tcpServer.stop();
        if (hasUDP())
            udpServer.stop();
        workerBoss.changeStatus(WorkerStatus.TERMINATING);

        batches.values().forEach(Batch::close);
        batches.clear();

        settings.destroy();
    }

    @Override
    public Class<? extends CerberusService> serviceClass() {
        return AlexandriaServer.class;
    }

    @Override
    public Collection<Thread> getThreads() {
        return workerBoss.getThreads();
    }
}
