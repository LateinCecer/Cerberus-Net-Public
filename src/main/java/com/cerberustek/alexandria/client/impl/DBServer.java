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

package com.cerberustek.alexandria.client.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.impl.elements.DocElement;
import com.cerberustek.data.impl.elements.StringElement;
import com.cerberustek.data.impl.tags.StringTag;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.ExceptionEvent;
import com.cerberustek.exceptions.UnknownBatchException;
import com.cerberustek.ConnectionType;
import com.cerberustek.alexandria.client.AlexandriaClient;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.MetaReplChannel;
import com.cerberustek.events.NetRequestReceptionEvent;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.querry.QueryResult;
import com.cerberustek.querry.trace.QueryTrace;
import com.cerberustek.querry.trace.impl.SuccessResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

@EventHandler(events = {
        NetRequestReceptionEvent.class
})
public class DBServer implements EventListener {

    private final InetSocketAddress serverAddress;
    private final HashMap<Short, MetaReplChannel> tcpReplChannels = new HashMap<>();
    private final HashMap<Short, MetaReplChannel> udpReplChannels = new HashMap<>();
    private final DiscriminatorMap discriminatorMap;

    private NetValve tcpvalve;
    private NetValve udpvalve;

    public DBServer(InetSocketAddress address, DiscriminatorMap discriminatorMap) {
        this.serverAddress = address;
        this.discriminatorMap = discriminatorMap;
    }

    public boolean hasTCP() {
        return tcpvalve != null && !tcpvalve.getPipeline().isClosed();
    }

    public boolean hasUDP() {
        return udpvalve != null && !udpvalve.getPipeline().isClosed();
    }

    public void setTCPValve(NetValve valve) {
        this.tcpvalve = valve;
    }

    public void setUDPValve(NetValve valve) {
        this.udpvalve = valve;
    }

    @SuppressWarnings("Duplicates")
    public MetaReplChannel createMetaChannel(short channelId, ConnectionType type) {
        if (type == ConnectionType.TCP) {
            MetaReplChannel channel = tcpReplChannels.get(channelId);
            if (channel == null) {
                channel = new MetaReplChannel(channelId, tcpvalve, discriminatorMap);
                tcpReplChannels.put(channelId, channel);
            }
            return channel;
        } else {
            MetaReplChannel channel = udpReplChannels.get(channelId);
            if (channel == null) {
                channel = new MetaReplChannel(channelId, udpvalve, discriminatorMap);
                udpReplChannels.put(channelId, channel);
            }
            return channel;
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetRequestReceptionEvent) {
            MetaReplChannel channel = (MetaReplChannel) ((NetRequestReceptionEvent) event).getReplyChannel();

            if (channel.getValve().getPipeline().getConnectionType() == ConnectionType.TCP) {
                if (!tcpReplChannels.containsKey(channel.getChannelId()))
                    return false;
            } else {
                if (!udpReplChannels.containsKey(channel.getChannelId()))
                    return false;
            }

            MetaData[] request = ((NetRequestReceptionEvent) event).getData();
            try {
                QueryResult[] result = queryData(request);
                if (result == null)
                    CerberusRegistry.getInstance().critical("Data query returned null without exception." +
                            " This should never happen. Something is profoundly wrong here. Will send" +
                            " back null non the less.");
                sendReply(channel, ((NetRequestReceptionEvent) event).getRequestId(), result);
            } catch (IllegalAccessException e) {
                getEventService().executeFullEIF(new ExceptionEvent(AlexandriaClient.class, e));
                CerberusRegistry.getInstance().warning("Client " + this.toString()
                        + " attempted to access batch illegally");

                sendReply(channel, ((NetRequestReceptionEvent) event).getRequestId(),
                        new SuccessResult(false), new StringElement("access_denied"));
            } catch (UnknownBatchException e) {
                getEventService().executeFullEIF(new ExceptionEvent(AlexandriaClient.class, e));
                CerberusRegistry.getInstance().warning("Client " + this.toString()
                        + " tried to access non-existing batch");

                sendReply(channel, ((NetRequestReceptionEvent) event).getRequestId(),
                        new SuccessResult(false), new StringElement("unknown_batch"));
            }
        }
        return false;
    }

    public MetaData[] sendQueryRequest(short channelId, ConnectionType connectionType,
                                          String batch, QueryTrace... traces) {
        DocElement meta = new DocElement();
        meta.insert(new StringTag("batch", batch));

        MetaData[] requests = new MetaData[traces.length + 1];
        requests[0] = meta;
        System.arraycopy(traces, 0, requests, 1, traces.length);

        return sendRequest(channelId, connectionType, requests);
    }

    public MetaData[] sendRequest(short channelId, ConnectionType connectionType, MetaData... request) {
        MetaReplChannel channel = createMetaChannel(channelId, connectionType);
        try {
            return channel.request(request);
        } catch (InterruptedException | IOException | NoMatchingDiscriminatorException e) {
            CerberusRegistry.getInstance().warning("Unable to send request to server. Cause: " + e);
            getEventService().executeFullEIF(new ExceptionEvent(AlexandriaClient.class, e));
        }
        return null;
    }

    public void sendReply(MetaReplChannel channel, int requestId, MetaData... reply) {
        try {
            channel.reply(requestId, reply);
        } catch (NoMatchingDiscriminatorException | IOException e) {
            CerberusRegistry.getInstance().warning("Unable to send reply to server. Cause: " + e);
            getEventService().executeFullEIF(new ExceptionEvent(AlexandriaClient.class, e));
        }
    }

    private CerberusEvent getEventService() {
        return CerberusRegistry.getInstance().getService(CerberusEvent.class);
    }

    private QueryResult[] queryData(MetaData... data) throws IllegalAccessException {
        CerberusRegistry.getInstance().warning("The server tried to send a query request to this client. This" +
                " feature has not been implemented on the current version of this client. Is the client out" +
                " of data? Please make sure to use the newest version of Alexandria.");
        return new QueryResult[] {new SuccessResult(false)};
    }
}
