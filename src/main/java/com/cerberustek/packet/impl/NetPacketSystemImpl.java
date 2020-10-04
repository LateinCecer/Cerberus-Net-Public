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

package com.cerberustek.packet.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetMetaChannel;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.MetaRequestListener;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.MetaElement;
import com.cerberustek.data.impl.elements.DocElement;
import com.cerberustek.data.impl.tags.ShortTag;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.NetDataReceptionEvent;
import com.cerberustek.packet.NetPacket;
import com.cerberustek.packet.NetPacketSystem;

import java.util.HashMap;

@SuppressWarnings("rawtypes")
@EventHandler(events = {
        NetDataReceptionEvent.class
})
public class NetPacketSystemImpl implements NetPacketSystem, MetaRequestListener, EventListener {

    private final static String PACKET_PAYLOAD = "payload";
    private final static String PACKET_ID = "id";

    private final HashMap<Short, NetPacket> packetMap = new HashMap<>();
    private final HashMap<Class<? extends NetPacket>, Short> idMap = new HashMap<>();
    private final NetMetaChannel channel;

    public NetPacketSystemImpl(NetMetaChannel channel) {
        this.channel = channel;
        CerberusRegistry.getInstance().getService(CerberusEvent.class).addListener(this);
    }

    @Override
    public void registerPacket(NetPacket packet, short id) {
        if (!packetMap.containsKey(id)) {
            packetMap.put(id, packet);
            idMap.put(packet.getClass(), id);
        }
    }

    @Override
    public void unregisterPacket(short id) {
        NetPacket packet = packetMap.get(id);
        if (packet != null)
            idMap.remove(packet.getClass());
        packetMap.remove(id);
    }

    @Override
    public MetaData format(MetaElement payload, Class<? extends NetPacket> clazz) {
        if (idMap.containsKey(clazz)) {
            DocElement doc = new DocElement();
            doc.insert(payload.toTag(PACKET_PAYLOAD));
            doc.insert(new ShortTag(PACKET_ID, idMap.get(clazz)));

            return doc;
        }
        return null;
    }

    @Override
    public MetaData process(MetaData raw, NetValve src) {
        if (raw instanceof DocElement &&
                ((DocElement) raw).contains(PACKET_ID) &&
                ((DocElement) raw).contains(PACKET_PAYLOAD)) {

            short id = ((DocElement) raw).extractShort(PACKET_ID).get();
            MetaElement payload = ((DocElement) raw).extract(PACKET_PAYLOAD).toElement();
            return process(payload, id, src);
        }
        return null;
    }

    @Override
    public MetaData process(MetaElement payload, short id, NetValve src) {
        NetPacket packet = packetMap.get(id);

        if (packet != null)
            return packet.process(payload, src);
        return null;
    }

    @Override
    public MetaData receive(NetValve requester, MetaData request) {
        return process(request, requester);
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetDataReceptionEvent &&
                ((NetDataReceptionEvent) event).getMetaChannel().equals(channel)) {

            process(((NetDataReceptionEvent) event).getData(), ((NetDataReceptionEvent) event).getValve());
            return true;
        }
        return false;
    }
}
