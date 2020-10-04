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

package com.cerberustek.server.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetChannel;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.InputChannel;
import com.cerberustek.channel.impl.OutputChannel;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.exceptions.ClientTimeoutException;
import com.cerberustek.worker.Startable;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.events.NetPostConnectionEvent;
import com.cerberustek.events.NetReceptionEvent;
import com.cerberustek.server.CerberusServer;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

@EventHandler(events = {NetReceptionEvent.class, NetPostConnectionEvent.class, NetDisconnectionEvent.class})
public class ServerHandshakeHandler implements EventListener, Startable {

    private HashMap<NetValve, Long> timeOuts = new HashMap<>();
    private HashMap<NetValve, Integer> pings = new HashMap<>();

    private final short channel;
    private final CerberusServer server;

    private WorkerBoss boss;
    private WorkerTask task;
    private String group;

    ServerHandshakeHandler(short channel, CerberusServer server) {
        this.channel = channel;
        this.server = server;
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetReceptionEvent) {
            NetReceptionEvent receptionEvent = (NetReceptionEvent) event;
            if (receptionEvent.getChannel().getChannelId() == channel) {
                timeOuts.replace(((NetReceptionEvent) event).getValve(), System.currentTimeMillis());
                // System.out.println("got data from client!");

                /*InputChannel channel = (InputChannel) receptionEvent.getChannel();
                pings.replace(receptionEvent.getValve(), (int) (System.currentTimeMillis() - channel.readLong()));*/
            }
        } else if (event instanceof NetPostConnectionEvent) {
            timeOuts.put(((NetPostConnectionEvent) event).getValve(), System.currentTimeMillis());
            pings.put(((NetPostConnectionEvent) event).getValve(), 0);
        } else if (event instanceof NetDisconnectionEvent) {
            timeOuts.remove(((NetDisconnectionEvent) event).getValve());
            pings.remove(((NetDisconnectionEvent) event).getValve());
        }
        return true;
    }

    @Override
    public void start(WorkerBoss boss, String group, WorkerPriority priority) {
        this.boss = boss;
        this.group = group;
        this.task = boss.submitTask(this::handshake, priority, group, -1, server.getHandshakeInterval());
    }

    private synchronized void handshake(double time, int rep) {
        final long currentTime = System.currentTimeMillis();
        try {
            timeOuts.forEach((valve, last) -> {
                int passed = (int) (System.currentTimeMillis() - last) - server.getHandshakeInterval();
                if (passed > server.getTimeOutDelay()) {
                    CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                            new NetDisconnectionEvent(valve, new ClientTimeoutException(valve, passed)));
                    try {
                        valve.getPipeline().close();
                    } catch (IOException e) {
                        //ignore
                    }
                    server.getNetServer().ban(valve.getPipeline().getRemoteAddress(), server.getTimeOutDelay());
                }
            });
        } catch (ConcurrentModificationException e) {
            // Ignore
        }

        try {
            server.getNetServer().getValves().forEach(valve -> {
                NetChannel c = valve.openChannel(channel);
                if (c != null) {

                    OutputChannel output = valve.findOutputChannel(channel);
                    output.writeLong(currentTime);
                    // System.out.println("Send handshake to " + valve.getPipeline().getRemoteAddress());
                    output.flush();

                    InputChannel input = valve.findInputChannel(channel);
                    if (input.available() >= 8) {
                        try {
                            pings.replace(valve, (int) (System.currentTimeMillis() - input.readLong(10)));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (ConcurrentModificationException e) {
            // Ignore
        }
    }

    public int getPing(NetValve valve) {
        if (pings.containsKey(valve))
            return pings.get(valve);
        return -1;
    }

    @Override
    public void stop() {
        boss.decomissionTask(task, group);
        timeOuts.clear();
        pings.clear();
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public WorkerBoss getBoss() {
        return boss;
    }

    @Override
    public WorkerTask getTask() {
        return task;
    }
}
