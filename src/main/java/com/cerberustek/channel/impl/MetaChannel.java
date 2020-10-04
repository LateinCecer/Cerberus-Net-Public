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

import com.cerberustek.CerberusData;
import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaByteBuffer;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.MetaOutputStream;
import com.cerberustek.data.impl.buffer.MetaByteBufferImpl;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.channel.NetMetaChannel;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetDataReceptionEvent;
import com.cerberustek.events.NetReceptionEvent;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.exception.UnknownDiscriminatorException;

import java.io.IOException;
import java.nio.ByteBuffer;

@EventHandler(events = {NetReceptionEvent.class})
public class MetaChannel implements Runnable, NetMetaChannel, EventListener {

    protected final DiscriminatorMap map;
    protected final MetaOutputStream outputStream;

    protected final InputChannel inputChannel;
    protected final OutputChannel outputChannel;

    protected final NetValve valve;

    protected ByteBuffer metaBuffer;

    public MetaChannel(short channelId, NetValve valve, DiscriminatorMap map) {
        this.valve = valve;

        valve.openChannel(channelId);
        inputChannel = valve.findInputChannel(channelId);
        outputChannel = valve.findOutputChannel(channelId);
        this.map = map;
        outputStream = CerberusData.createOutputStream(outputChannel, map);
    }

    @Override
    public void run() {
        try {
//            CerberusRegistry.getInstance().debug("Start reading form meta channel");
            MetaByteBuffer buffer = new MetaByteBufferImpl(map, metaBuffer);
            MetaData data = buffer.readData();
//            CerberusRegistry.getInstance().debug("Read data from meta channel");
            if (data == null)
                return;

            metaBuffer = null;

            // System.out.println("Received data: " + data);
            CerberusRegistry.getInstance().getService(CerberusEvent.class)
                    .executeFullEIF(new NetDataReceptionEvent(data, valve, inputChannel, this));
        } catch (UnknownDiscriminatorException e) {
            CerberusRegistry.getInstance().warning("Failed to read meta data from input stream!");
        }
    }

    public MetaOutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void start() {
        CerberusRegistry.getInstance().getService(CerberusEvent.class).addListener(this);
    }

    @Override
    public void stop() {
        try {
            inputChannel.getBuffer().notifyAll();
        } catch (IllegalMonitorStateException ignore) {}
        CerberusRegistry.getInstance().getService(CerberusEvent.class).removeListener(this);
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
        outputChannel.flush();
    }

    @Override
    public void send(MetaData data) throws IOException, NoMatchingDiscriminatorException {
        outputStream.writeInt((int) CerberusData.totalSize(data));
        outputStream.writeData(data);
        flush();
    }

    @Override
    public NetValve getValve() {
        return valve;
    }

    @Override
    public DiscriminatorMap getDiscriminatorMap() {
        return map;
    }

    @Override
    public short getChannelId() {
        return inputChannel.getChannelId();
    }

    @Override
    public void close() throws IOException {
        CerberusRegistry.getInstance().getService(CerberusEvent.class).removeListener(this);
        flush();
        valve.closeChannel(inputChannel.getChannelId());
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetReceptionEvent) {

            if (((NetReceptionEvent) event).getChannel() != null &&
                    ((NetReceptionEvent) event).getValve() != null &&
                    inputChannel != null &&
                    ((NetReceptionEvent) event).getChannel().getChannelId() == inputChannel.getChannelId() &&
                    ((NetReceptionEvent) event).getValve().equals(valve)) {

                if (inputChannel.isPackageStart()) {
                    try {
                        int length = inputChannel.readInt();
                        metaBuffer = ByteBuffer.allocate(length);

                        transferData();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (metaBuffer != null) {
                    try {
                        transferData();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void transferData() throws IOException {
        int i = Math.min(inputChannel.available(), metaBuffer.remaining());
        byte[] data = new byte[i];
        i = inputChannel.read(data);
        metaBuffer.put(data, 0, i);

        if (metaBuffer.remaining() == 0) {
            metaBuffer.flip();
            run();
        }
    }
}