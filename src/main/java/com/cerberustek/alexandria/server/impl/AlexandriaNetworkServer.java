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

import com.cerberustek.CerberusRegistry;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.alexandria.server.AlexandriaServer;
import com.cerberustek.events.NetConnectionEvent;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.events.NetPostConnectionEvent;
import com.cerberustek.exception.UnknownDiscriminatorException;
import com.cerberustek.tcp.TCPPipeline;
import com.cerberustek.udp.UDPPipeline;
import com.cerberustek.utils.DiscriminatorFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;

@EventHandler(events = {
        NetConnectionEvent.class,
        NetDisconnectionEvent.class
})
public class AlexandriaNetworkServer implements EventListener {

    private final HashMap<InetAddress, DBClient> dbClients = new HashMap<>();
    private final DiscriminatorMap networkDiscriminators;

    public AlexandriaNetworkServer() {
        AlexandriaServer server = CerberusRegistry.getInstance().getService(AlexandriaServer.class);
        File disFile = new File(server.getSettings().getString("network_discriminators", "network.mdf"));
        DiscriminatorFile discriminatorFile = new DiscriminatorFile(disFile);

        if (!disFile.exists() || !disFile.isFile()) {
            try {
                discriminatorFile.writeDefault();
            } catch (IOException | UnknownDiscriminatorException e) {
                throw new IllegalStateException("Unable to write default network discriminators!");
            }
        }

        try {
            networkDiscriminators = discriminatorFile.read();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to read network discriminators!");
        }
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetPostConnectionEvent) {
            CerberusRegistry.getInstance().info("Client "
                    + ((NetPostConnectionEvent) event).getValve().getPipeline().getRemoteAddress()
                    + " connected to DB server");

            SocketAddress address = ((NetPostConnectionEvent) event).getValve().getPipeline().getRemoteAddress();
            if (!(address instanceof InetSocketAddress))
                return false;

            DBClient client = dbClients.get(((InetSocketAddress) address).getAddress());
            if (client == null)
                client = new DBClient(networkDiscriminators);

            // TCP
            if (((NetPostConnectionEvent) event).getValve().getPipeline() instanceof TCPPipeline) {
                client.setTcpValve(((NetPostConnectionEvent) event).getValve());
                CerberusRegistry.getInstance().debug("Added TCP valve to DB client " + client);
            }
            // UDP
            else if (((NetPostConnectionEvent) event).getValve().getPipeline() instanceof UDPPipeline) {
                client.setUdpValve(((NetPostConnectionEvent) event).getValve());
                CerberusRegistry.getInstance().debug("Added UDP valve to DB client " + client);
            }
            return true;
        } else if (event instanceof NetDisconnectionEvent) {
            SocketAddress address = ((NetDisconnectionEvent) event).getValve().getPipeline().getRemoteAddress();
            if (!(address instanceof InetSocketAddress))
                return false;

            DBClient client = dbClients.get(((InetSocketAddress) address).getAddress());
            if (client == null)
                return false;

            // TCP
            if (((NetDisconnectionEvent) event).getValve().getPipeline() instanceof TCPPipeline) {
                client.setTcpValve(null);
                CerberusRegistry.getInstance().info("Removed TCP valve from DB client " + client);
            }
            // UDP
            else if (((NetDisconnectionEvent) event).getValve().getPipeline() instanceof UDPPipeline) {
                client.setUdpValve(null);
                CerberusRegistry.getInstance().info("Removed UDP valve from DB client " + client);
            }

            if (!client.hasTCP() && !client.hasUDP()) {
                dbClients.remove(((InetSocketAddress) address).getAddress());
                CerberusRegistry.getInstance().info("Client "
                        + ((NetDisconnectionEvent) event).getValve().getPipeline().getRemoteAddress()
                        + " disconnected from DB server");
            }
            return true;
        }
        return false;
    }
}
