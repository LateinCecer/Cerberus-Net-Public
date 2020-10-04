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

package com.cerberustek.events;

import com.cerberustek.channel.NetValve;
import com.cerberustek.event.Event;

public class NetChannelCreationEvent implements Event {

    private final NetValve valve;
    private final short channelId;

    public NetChannelCreationEvent(NetValve valve, short channelId) {
        this.valve = valve;
        this.channelId = channelId;
    }

    public NetValve getValve() {
        return valve;
    }

    public short getChannelId() {
        return channelId;
    }
}
