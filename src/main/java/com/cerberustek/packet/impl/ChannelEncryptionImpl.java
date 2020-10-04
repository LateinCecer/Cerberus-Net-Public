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

import com.cerberustek.packet.ChannelEncryption;

import java.util.UUID;

public class ChannelEncryptionImpl implements ChannelEncryption {

    private final UUID uuid;
    private final short channel;

    private long lastUpdate;

    public ChannelEncryptionImpl(UUID uuid, short channel) {
        this.uuid = uuid;
        this.channel = channel;
    }

    void renew() {
        lastUpdate = System.currentTimeMillis();
    }

    @Override
    public UUID getKeyId() {
        return uuid;
    }

    @Override
    public long lastUpdate() {
        return lastUpdate;
    }

    @Override
    public short channelId() {
        return channel;
    }
}
