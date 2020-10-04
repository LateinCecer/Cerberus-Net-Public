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

package com.cerberustek.channel.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaByteBuffer;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.impl.buffer.MetaByteBufferImpl;
import com.cerberustek.data.impl.elements.DocElement;
import com.cerberustek.data.impl.tags.ArrayTag;
import com.cerberustek.data.impl.tags.BooleanTag;
import com.cerberustek.data.impl.tags.IntTag;
import com.cerberustek.event.EventHandler;
import com.cerberustek.channel.NetMetaReplChannel;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetReceptionEvent;
import com.cerberustek.events.NetRequestReceptionEvent;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.exception.UnknownDiscriminatorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

@EventHandler(events = {
        NetReceptionEvent.class
})
public class MetaReplChannel extends MetaChannel implements NetMetaReplChannel {

    private final HashMap<Integer, MetaData[]> replyMap = new HashMap<>();
    private final HashSet<Integer> requestSet = new HashSet<>();
    private final CerberusEvent eventService;

    public MetaReplChannel(short channelId, NetValve valve, DiscriminatorMap map) {
        super(channelId, valve, map);
        eventService = CerberusRegistry.getInstance().getService(CerberusEvent.class);
    }

    @Override
    public MetaData[] request(MetaData... data) throws InterruptedException, NoMatchingDiscriminatorException, IOException {
        int id = sendRequestPrimer(data);

        while (requestSet.contains(id)) {
            synchronized (this) {
                wait();
            }
        }

        MetaData[] reply = replyMap.get(id);
        replyMap.remove(id);
        return reply;
    }

    @Override
    public MetaData[] request(int halt, MetaData... data) throws InterruptedException, NoMatchingDiscriminatorException, IOException {
        int id = sendRequestPrimer(data);

        long startTime = System.currentTimeMillis();
        long currentTime = startTime;
        while (requestSet.contains(id)) {
            synchronized (this) {
                wait(halt - currentTime + startTime);
            }

            currentTime = System.currentTimeMillis();
            if (halt <= currentTime - startTime)
                requestSet.remove(id);
        }

        MetaData[] reply = replyMap.get(id);
        replyMap.remove(id);
        return reply;
    }

    private int sendRequestPrimer(MetaData... data) throws IOException, NoMatchingDiscriminatorException {
        int id = findNextId();
        MetaData packet = format(id, false, data);

        requestSet.add(id);
        send(packet);
        flush();
        return id;
    }

    @Override
    public void reply(int id, MetaData... data) throws NoMatchingDiscriminatorException, IOException {
        send(format(id, true, data));
    }

    private int findNextId() {
        int out = 0;
        while (requestSet.contains(out))
            out++;
        return out;
    }

    private MetaData format(int id, boolean isReply, MetaData... data) {
        DocElement out = new DocElement();

        out.insert(new ArrayTag<>("payload", data));
        out.insert(new IntTag("id", id));
        out.insert(new BooleanTag("reply", isReply));

        return out;
    }

    private Integer extractId(MetaData raw) {
        if (raw instanceof DocElement) {
            IntTag value = ((DocElement) raw).extractInt("id");
            return value != null ? value.get() : null;
        }
        return null;
    }

    private MetaData[] extractPayload(MetaData raw) {
        if (raw instanceof DocElement) {
            ArrayTag<?> value = ((DocElement) raw).extractArray("payload");
            return value != null ? value.get() : null;
        }
        return null;
    }

    private Boolean extractIsReply(MetaData raw) {
        if (raw instanceof DocElement) {
            BooleanTag value = ((DocElement) raw).extractBoolean("reply");
            return value != null ? value.get() : null;
        }
        return null;
    }

    @Override
    public void run() {
        try {
            MetaByteBuffer buffer = new MetaByteBufferImpl(map, metaBuffer);
            MetaData data = buffer.readData();
            if (data == null)
                return;

            metaBuffer = null;

            Boolean isReply = extractIsReply(data);
            if (isReply == null)
                return;

            Integer id = extractId(data);
            MetaData[] payload = extractPayload(data);

            if (id == null)
                return;

            if (isReply) {
                replyMap.put(id, payload);
                requestSet.remove(id);

                synchronized (this) {
                    this.notifyAll();
                }
            } else
                eventService.executeFullEIF(new NetRequestReceptionEvent(this, id, payload));
        } catch (UnknownDiscriminatorException e) {
            CerberusRegistry.getInstance().warning("Failed to read meta data from input stream!");
        }
    }
}
