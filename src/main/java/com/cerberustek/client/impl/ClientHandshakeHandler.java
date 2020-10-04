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

package com.cerberustek.client.impl;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetChannel;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.InputChannel;
import com.cerberustek.channel.impl.OutputChannel;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.NetDisconnectionEvent;
import com.cerberustek.events.NetReceptionEvent;
import com.cerberustek.exceptions.ClientTimeoutException;
import com.cerberustek.worker.Startable;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;
import com.cerberustek.client.CerberusClient;
import com.cerberustek.client.NetClient;

@EventHandler(events = {NetReceptionEvent.class})
public class ClientHandshakeHandler implements EventListener, Startable {

    private final CerberusClient client;
    private final short channel;

    private WorkerBoss boss;
    private WorkerTask task;
    private String group;
    private long lastHandshake;
    private int ping;

    public ClientHandshakeHandler(CerberusClient client, short channel) {
        this.client = client;
        this.channel = channel;
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetReceptionEvent) {
            NetReceptionEvent reception = (NetReceptionEvent) event;

            if (reception.getChannel().getChannelId() == channel) {
                // System.out.println("received handshake");
                lastHandshake = System.currentTimeMillis();
            }
        }
        return true;
    }

    @Override
    public void start(WorkerBoss boss, String group, WorkerPriority priority) {
        this.boss = boss;
        this.group = group;
        this.task = boss.submitTask(this::timeOut, priority, group, -1, client.getHandshakeInterval());
        lastHandshake = System.currentTimeMillis();
        ping = 0;
    }

    private void timeOut(double time, int rep) {
        // System.out.println("Timeout check: " + rep);
        int passed = (int) (System.currentTimeMillis() - lastHandshake);
        if (passed > client.getTimeOutDelay()) {
            CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                    new NetDisconnectionEvent(client.getNetClient().getValve(),
                            new ClientTimeoutException(client.getNetClient().getValve(), passed)));
            client.disconnect();
        }
        handshake(time);
    }

    private void handshake(double time) {
        // System.out.println("starting handshake");
        NetChannel c = client.getNetClient()
                .getValve()
                .openChannel(channel);

        if (c != null) {
            OutputChannel outputChannel = client.getNetClient().getValve().findOutputChannel(channel);
            outputChannel.writeLong(System.currentTimeMillis());
            // System.out.println("Sending data...");
            outputChannel.flush();

            NetClient nClient = client.getNetClient();
            if (nClient == null)
                return;
            NetValve valve = nClient.getValve();
            if (valve == null)
                return;
            InputChannel inputChannel = valve.findInputChannel(channel);
            if (inputChannel.available() >= 8) {
                try {
                    ping = (int) (System.currentTimeMillis() - inputChannel.readLong(10));

                    if (ping > client.getTimeOutDelay()) {
                        CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIT(
                                new NetDisconnectionEvent(client.getNetClient().getValve(),
                                        new ClientTimeoutException(client.getNetClient().getValve(), ping)));
                        client.disconnect();
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
        // System.out.println("Finished handshake");
    }

    public int getPing() {
        return ping;
    }

    @Override
    public void stop() {
        boss.decomissionTask(task, group);
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
