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

import com.cerberustek.channel.NetMetaReplChannel;
import com.cerberustek.data.MetaData;
import com.cerberustek.event.Event;

public class NetRequestReceptionEvent implements Event {

    private final MetaData[] data;
    private final NetMetaReplChannel replyChannel;
    private final int requestId;

    public NetRequestReceptionEvent(NetMetaReplChannel replyChannel, int requestId, MetaData... data) {
        this.data = data;
        this.requestId = requestId;
        this.replyChannel = replyChannel;
    }

    public int getRequestId() {
        return requestId;
    }

    public MetaData[] getData() {
        return data;
    }

    public NetMetaReplChannel getReplyChannel() {
        return replyChannel;
    }
}
