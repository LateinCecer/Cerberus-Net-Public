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

package com.cerberustek.command;

import com.cerberustek.CerberusRegistry;
import com.cerberustek.service.TerminalUtil;
import com.cerberustek.service.terminal.TerminalCommand;
import com.cerberustek.usr.PermissionHolder;
import com.cerberustek.CerberusNet;
import com.cerberustek.channel.NetValve;
import com.cerberustek.server.CerberusServer;
import com.cerberustek.worker.WorkerStatus;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Scanner;

public class ServerCommand implements TerminalCommand {

    @Override
    public boolean execute(PermissionHolder permissionHolder, Scanner scanner, String... args) {
        if (args.length > 0) {
            CerberusRegistry registry = CerberusRegistry.getInstance();

            switch (args[0].toLowerCase()) {
                case "list":
                    if (!permissionHolder.hasPermission(CerberusNet.PERMISSION_SERVER_LIST)) {
                        registry.printNoPermission();
                        return true;
                    }

                    registry.info(TerminalUtil.ANSI_YELLOW + "Here is a list of all online servers:"
                            + TerminalUtil.ANSI_RESET);
                    CerberusNet net = registry.getService(CerberusNet.class);

                    for (CerberusServer server : net.getServers())
                        registry.info(TerminalUtil.ANSI_CYAN + "\t# " + TerminalUtil.ANSI_PURPLE +
                                server.getNetServer().getLocalAddress() + TerminalUtil.ANSI_RESET +
                                " > " + TerminalUtil.ANSI_PURPLE + server.getConnectionType() + TerminalUtil.ANSI_RESET);
                    break;
                default:
                    return false;
            }

            if (args.length > 1) {
                SocketAddress address;
                try {
                    String[] s = args[0].split(":");
                    address = new InetSocketAddress(s[0], Integer.parseInt(s[1]));
                } catch (Exception e) {
                    registry.critical("Invalid socket address: " + args[1]);
                    return true;
                }

                CerberusNet net = registry.getService(CerberusNet.class);
                CerberusServer target = null;
                for (CerberusServer server : net.getServers()) {
                    if (server.getNetServer().getLocalAddress().equals(address)) {
                        target = server;
                        break;
                    }
                }

                if (target == null) {
                    registry.critical("Server with name: " + args[0] + " not found!");
                    return false;
                }

                switch (args[1].toLowerCase()) {
                    case "clients":
                        if (!permissionHolder.hasPermission(CerberusNet.PERMISSION_SERVER_LIST)) {
                            registry.printNoPermission();
                            return true;
                        }

                        registry.info(TerminalUtil.ANSI_YELLOW + "Here is a list of all active clients:" +
                                TerminalUtil.ANSI_RESET);
                        Collection<NetValve> clients = target.getNetServer().getValves();

                        for (NetValve client : clients) {
                            registry.info(TerminalUtil.ANSI_CYAN + "\t# " + TerminalUtil.ANSI_PURPLE +
                                    client.getPipeline().getRemoteAddress() + TerminalUtil.ANSI_RESET +
                                    " ping: " + TerminalUtil.ANSI_PURPLE + target.getPing(client) +
                                    TerminalUtil.ANSI_RESET + "ms!");
                        }
                        break;
                    case "stop":
                        if (!permissionHolder.hasPermission(CerberusNet.PERMISSION_SERVER_STOP)) {
                            registry.printNoPermission();
                            return true;
                        }

                        registry.info("Stopping server " + args[0] + "...");
                        target.stop();
                        target.getWorkerBoss().changeStatus(WorkerStatus.TERMINATING);
                        registry.info("successfully stopped server!");
                    default:
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String executor() {
        return "server";
    }

    @Override
    public String usage() {
        return "server <list/name> <clients/stop>";
    }

    @Override
    public String requiredPermission() {
        return CerberusNet.PERMISSION_SERVER;
    }
}
