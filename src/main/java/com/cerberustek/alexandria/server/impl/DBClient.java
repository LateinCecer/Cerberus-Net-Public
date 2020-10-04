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
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.impl.elements.DocElement;
import com.cerberustek.data.impl.elements.StringElement;
import com.cerberustek.data.impl.tags.CipherTag;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.ExceptionEvent;
import com.cerberustek.exceptions.UnknownBatchException;
import com.cerberustek.ConnectionType;
import com.cerberustek.alexandria.Alexandria;
import com.cerberustek.alexandria.server.AlexandriaServer;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.MetaReplChannel;
import com.cerberustek.events.NetRequestReceptionEvent;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.exception.ResourceUnavailableException;
import com.cerberustek.querry.QueryResult;
import com.cerberustek.querry.trace.QueryTrace;
import com.cerberustek.querry.trace.impl.SuccessResult;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

@EventHandler(events = {
        NetRequestReceptionEvent.class
})
public class DBClient implements EventListener {

    private final HashMap<String, Batch> batches = new HashMap<>();
    private final HashMap<Short, MetaReplChannel> tcpReplChannels = new HashMap<>();
    private final HashMap<Short, MetaReplChannel> udpReplChannels = new HashMap<>();
    private final DiscriminatorMap discriminatorMap;

    private NetValve tcpValve;
    private NetValve udpValve;

    private CerberusEvent eventService;

    public DBClient(DiscriminatorMap discriminatorMap) {
        this.discriminatorMap = discriminatorMap;
    }

    private CerberusEvent getEventService() {
        if (eventService == null)
            eventService = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        return eventService;
    }

    public boolean hasTCP() {
        return tcpValve != null && !tcpValve.getPipeline().isClosed();
    }

    public boolean hasUDP() {
        return udpValve != null && !udpValve.getPipeline().isClosed();
    }

    public void setTcpValve(NetValve valve) {
        tcpValve = valve;
    }

    public void setUdpValve(NetValve valve) {
        udpValve = valve;
    }

    @SuppressWarnings("Duplicates")
    private MetaReplChannel createMetaChannel(short channelId, ConnectionType type) {
        if (type == ConnectionType.TCP && hasTCP()) {
            MetaReplChannel channel = tcpReplChannels.get(channelId);
            if (channel == null) {
                channel = new MetaReplChannel(channelId, tcpValve, discriminatorMap);
                tcpReplChannels.put(channelId, channel);
            }
            return channel;
        } else {
            MetaReplChannel channel = udpReplChannels.get(channelId);
            if (channel == null) {
                channel = new MetaReplChannel(channelId, udpValve, discriminatorMap);
                udpReplChannels.put(channelId, channel);
            }
            return channel;
        }
    }

    public MetaReplChannel getCommandChannel(ConnectionType type) {
        return createMetaChannel(Alexandria.COMMAND_CHANNEL_ID, type);
    }

    private QueryResult[] queryData(MetaData... data) throws IllegalAccessException {
        if (!(data[0] instanceof DocElement))
            throw new IllegalArgumentException("No valid meta appendage for DB request");

        DocElement meta = (DocElement) data[0];
        String batchName = meta.extractString("batch").get();
        Batch batch = batches.get(batchName);
        if (batch == null) {
            @SuppressWarnings("unchecked") CipherTag<StringElement> passwdContainer
                    = (CipherTag<StringElement>) meta.extract("passwd");
            if (passwdContainer != null) {
                try {
                    batch = CerberusRegistry.getInstance().getService(AlexandriaServer.class)
                            .openBatch(batchName, passwdContainer.get().get());
                } catch (FileNotFoundException e) {
                    throw new UnknownBatchException(batchName);
                }
                batches.put(batchName, batch);
            }

            if (batch == null)
                throw new IllegalAccessException("No access to Batch");
            else
                batches.put(batchName, batch);
        }

        QueryResult[] out = new QueryResult[data.length - 1];
        for (int i = 0; i < data.length; i++) {
            if (!(data[i + 1] instanceof QueryTrace))
                continue;

            try {
                out[i] = batch.query((QueryTrace) data[i + 1]);
            } catch (ResourceUnavailableException e) {
                getEventService().executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
                out[i] = null;
            }
        }
        return out;
    }

    private void sendReply(MetaReplChannel channel, int requestId, MetaData... reply) {
        try {
            channel.reply(requestId, reply);
        } catch (NoMatchingDiscriminatorException | IOException e) {
            CerberusRegistry.getInstance().warning("Unable to send reply to client. Cause: " + e);
            getEventService().executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
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
                getEventService().executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
                CerberusRegistry.getInstance().warning("Client " + this.toString()
                        + " attempted to access batch illegally");

                sendReply(channel, ((NetRequestReceptionEvent) event).getRequestId(),
                        new SuccessResult(false), new StringElement("access_denied"));
            } catch (UnknownBatchException e) {
                getEventService().executeFullEIF(new ExceptionEvent(AlexandriaServer.class, e));
                CerberusRegistry.getInstance().warning("Client " + this.toString()
                        + " tried to access non-existing batch");

                sendReply(channel, ((NetRequestReceptionEvent) event).getRequestId(),
                        new SuccessResult(false), new StringElement("unknown_batch"));
            }
            return true;
        }
        return false;
    }
}
