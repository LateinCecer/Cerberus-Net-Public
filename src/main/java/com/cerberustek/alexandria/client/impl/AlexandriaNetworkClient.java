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
import com.cerberustek.Destroyable;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.events.NetFailedConnectionEvent;
import com.cerberustek.events.NetPostConnectionEvent;

@EventHandler(events = {
        NetDisconnectionEvent.class,
        NetPostConnectionEvent.class,
        NetFailedConnectionEvent.class
})
public class AlexandriaNetworkClient implements EventListener, Destroyable {

    AlexandriaNetworkClient() {
        CerberusRegistry.getInstance().getService(CerberusEvent.class).addListener(this);
    }

    @Override
    public boolean onEvent(Event event) {
        CerberusRegistry registry = CerberusRegistry.getInstance();

        if (event instanceof NetDisconnectionEvent) {
            registry.debug("Alexandria client was disconnected from server with ip: "
                    + ((NetDisconnectionEvent) event).getValve().getPipeline().getRemoteAddress());
        } else if (event instanceof NetPostConnectionEvent) {
            registry.debug("Alexandria client connected to server with ip: "
                    + ((NetPostConnectionEvent) event).getValve().getPipeline().getRemoteAddress());
        } else if (event instanceof NetFailedConnectionEvent) {
            registry.debug("Alexandria client failed to establish a connection to "
                    + ((NetFailedConnectionEvent) event).getRemoteAddress());
        }
        return false;
    }

    @Override
    public void destroy() {
        CerberusRegistry.getInstance().getService(CerberusEvent.class).removeListener(this);
    }
}
