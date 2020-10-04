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

package com.cerberustek;

import com.cerberustek.client.CerberusClient;
import com.cerberustek.client.impl.CerberusClientImpl;
import com.cerberustek.command.ClientCommand;
import com.cerberustek.command.ServerCommand;
import com.cerberustek.server.CerberusServer;
import com.cerberustek.server.impl.CerberusServerImpl;
import com.cerberustek.service.CerberusService;
import com.cerberustek.service.ServiceNotFoundException;
import com.cerberustek.service.terminal.TerminalExecutor;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerStatus;

import java.util.Collection;
import java.util.HashSet;

public class CerberusNet implements CerberusService {

    public static String PERMISSION = "de.cerberus.net";
    public static String PERMISSION_SERVER = PERMISSION + ".server";
    public static String PERMISSION_SERVER_LIST = PERMISSION_SERVER + ".list";
    public static String PERMISSION_SERVER_STOP = PERMISSION_SERVER + ".stop";
    public static String PERMISSION_CLIENT = PERMISSION + ".client";
    public static String PERMISSION_CLIENT_LIST = PERMISSION_CLIENT + ".list";

    public static CerberusServer createServer(ConnectionType type, WorkerBoss boss, int port, int backlog,
                                              int bufferCap, int connectionTimeout, int poolSize) {
        return getService().registerServer(new CerberusServerImpl(type, boss, port, backlog, bufferCap,
                connectionTimeout, poolSize));
    }

    public static CerberusServer createServer(ConnectionType type, WorkerBoss boss, int port, int backlog,
                                              int poolSize) {
        return getService().registerServer(new CerberusServerImpl(type, boss, port, backlog, poolSize));
    }

    public static CerberusServer createServer(ConnectionType type, int port, int backlog, int poolSize) {
        return getService().registerServer(new CerberusServerImpl(type, port, backlog, poolSize));
    }

    public static CerberusServer createServer(ConnectionType type, int port, int backlog) {
        return getService().registerServer(new CerberusServerImpl(type, port, backlog));
    }

    public static CerberusServer createServer(ConnectionType type, int port) {
        return getService().registerServer(new CerberusServerImpl(type, port));
    }

    public static CerberusClient createClient(ConnectionType type, WorkerBoss boss, String passphrase,
                                              int bufferCap) {
        return getService().registerClient(new CerberusClientImpl(type, boss, passphrase, bufferCap));
    }

    public static CerberusClient createClient(ConnectionType type, WorkerBoss boss, String passphrase) {
        return getService().registerClient(new CerberusClientImpl(type, boss, passphrase));
    }

    public static CerberusClient createClient(ConnectionType type, String passphrase) {
        return getService().registerClient(new CerberusClientImpl(type, passphrase));
    }

    private static CerberusNet getService() {
        CerberusNet service;
        try {
            service = CerberusRegistry.getInstance().getService(CerberusNet.class);
        } catch (ServiceNotFoundException e) {
            CerberusRegistry.getInstance().registerService(service = new CerberusNet());
            CerberusRegistry.getInstance().requestStart(CerberusNet.class);
        }
        return service;
    }

    private final HashSet<CerberusServer> servers = new HashSet<>();
    private final HashSet<CerberusClient> clients = new HashSet<>();

    public CerberusServer registerServer(CerberusServer server) {
        servers.add(server);
        return server;
    }

    public CerberusServer unregisterServer(CerberusServer server) {
        servers.remove(server);
        return server;
    }

    public CerberusClient registerClient(CerberusClient client) {
        clients.add(client);
        return client;
    }

    public CerberusClient unregisterClient(CerberusClient client) {
        clients.remove(client);
        return client;
    }

    @Override
    public void start() {
        CerberusRegistry.getInstance().info("Started Cerberus-Net!");
        TerminalExecutor executor = CerberusRegistry.getInstance().getTerminal().getExecutor();
        executor.registerCommand(new ServerCommand());
        executor.registerCommand(new ClientCommand());
    }

    @Override
    public void stop() {
        servers.forEach(server -> {
            try {
                server.stop();
            } catch (Exception e) {
                // ignore
            }

            WorkerBoss boss = server.getWorkerBoss();
            boss.changeStatus(WorkerStatus.TERMINATING);
        });

        clients.forEach(client -> {
            try {
                client.disconnect();
            } catch (Exception e) {
                // ignore
            }

            WorkerBoss boss = client.getWorkerBoss();
            boss.changeStatus(WorkerStatus.TERMINATING);
        });
    }

    public Collection<CerberusServer> getServers() {
        return servers;
    }

    public Collection<CerberusClient> getClients() {
        return clients;
    }

    @Override
    public Class<? extends CerberusService> serviceClass() {
        return getClass();
    }

    @Override
    public Collection<Thread> getThreads() {
        final HashSet<Thread> threads = new HashSet<>();

        servers.forEach(server -> threads.addAll(server.getWorkerBoss().getThreads()));
        clients.forEach(client -> threads.addAll(client.getWorkerBoss().getThreads()));
        return threads;
    }
}
