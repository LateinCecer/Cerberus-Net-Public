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

package com.cerberustek.valve;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.buffer.OverflowBuffer;
import com.cerberustek.channel.NetChannel;
import com.cerberustek.channel.NetPipeline;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.InputChannel;
import com.cerberustek.channel.impl.OutputChannel;
import com.cerberustek.events.NetChannelCreationEvent;
import com.cerberustek.events.NetReceptionEvent;
import com.cerberustek.worker.Startable;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;

import java.io.IOException;
import java.util.HashMap;

public abstract class NetValveBase implements NetValve, Startable {

    private final HashMap<Short, InputChannel> inputChannelMap = new HashMap<>();
    private final HashMap<Short, OutputChannel> outputChannelMap = new HashMap<>();
    private final NetPipeline pipeline;
    private final int bufferCapacity;

    private CerberusEvent eventHandler;
    protected WorkerTask inputTask;
    protected WorkerTask channelTask;
    private WorkerTask outputTask;
    protected WorkerBoss boss;
    protected WorkerPriority priority;
    protected String group;

    private int currentPackageLength;
    private OverflowBuffer buffer;

    public NetValveBase(NetPipeline pipeline, int bufferCapacity) {
        this.pipeline = pipeline;
        this.bufferCapacity = bufferCapacity;
        this.buffer = new OverflowBuffer(bufferCapacity * 4);
        this.currentPackageLength = 0;
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
        return inputTask;
    }

    @Override
    public void stop() {
        if (inputTask != null)
            boss.decomissionTask(inputTask, group);
        if (outputTask != null)
            boss.decomissionTask(outputTask, group);

        try {
            pipeline.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void updateOutputs() {
        if (outputTask == null && boss != null) {
            outputTask = boss.submitTask((delta) -> {
                try {
                    for (NetChannel channel : outputChannelMap.values())
                        ((OutputChannel) channel).update();
                } finally {
                    outputTask = null;
                }
            }, priority, group);
        }
    }

    @Override
    public synchronized void updateInputs(byte[] data, int off, int len) {
        buffer.write(data, off, len);
//        updateChannels();

        //noinspection StatementWithEmptyBody
        while (updateInputs());

        /*short channelId = (short) (((data[off] & 0xFF) << 8) + (data[off + 1] & 0xFF));

        InputChannel channel = findInputChannel(channelId);
        if (channel == null)
            channel = (InputChannel) openChannel(channelId);

        channel.process(data, off + 2, len - 2);
        getEventHandler().executeArithmetic(new NetReceptionEvent(this, channel));*/
    }

    @Override
    public synchronized void updateChannels() {
        if (channelTask == null && boss != null) {
            channelTask = boss.submitTask(delta -> {
                try {
                    //noinspection StatementWithEmptyBody
                    while (updateInputs()) ;
                } finally {
                    channelTask = null;
                }
            }, priority, "channel_update");
        }
    }

    private boolean updateInputs() {
        if (currentPackageLength == 0) {
            if (buffer.remaining() > 4) {
                int c1 = buffer.read();
                int c2 = buffer.read();
                int c3 = buffer.read();
                int c4 = buffer.read();

                currentPackageLength = c1 << 24 | ((c2 & 0xFF) << 16) | ((c3 & 0xFF) << 8) | (c4 & 0xFF);
            } else {
                return false;
            }
        }

        if (buffer.remaining() >= currentPackageLength) {
            byte[] data = new byte[currentPackageLength];
            buffer.read(data);

            short channelId = (short) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));

            InputChannel channel = findInputChannel(channelId);
            if (channel == null)
                channel = (InputChannel) openChannel(channelId);

            channel.process(data, 2, currentPackageLength - 2);



//            if (channelId == 3)
//                CerberusRegistry.getInstance().debug("NetReceptionEvent for channel " + channelId);
            getEventHandler().executeArithmetic(new NetReceptionEvent(this, channel));
            currentPackageLength = 0;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public short nextChannel() {
        short id = 0;
        while (inputChannelMap.containsKey(id))
            id++;
        return id;
    }

    @Override
    public InputChannel findInputChannel(short channelId) {
        return inputChannelMap.get(channelId);
    }

    @Override
    public OutputChannel findOutputChannel(short channelId) {
        return outputChannelMap.get(channelId);
    }

    @Override
    public NetChannel openChannel(short channelId) {
        InputChannel inputChannel = inputChannelMap.get(channelId);
        if (inputChannel == null && getEventHandler().executeShortEIF(
                new NetChannelCreationEvent(this, channelId))) {

            inputChannel = new InputChannel(channelId, bufferCapacity);
            OutputChannel outputChannel = new OutputChannel(channelId, bufferCapacity, pipeline, this);
            inputChannelMap.put(channelId, inputChannel);
            outputChannelMap.put(channelId, outputChannel);
            return inputChannel;
        }
        return inputChannel;
    }

    @Override
    public NetChannel openChannel() {
        return openChannel(nextChannel());
    }

    @Override
    public NetChannel closeChannel(short channelId) {
        outputChannelMap.remove(channelId);
        return inputChannelMap.remove(channelId);
    }

    @Override
    public NetPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public int getNetworkBufferSize() {
        return bufferCapacity;
    }

    private CerberusEvent getEventHandler() {
        if (eventHandler == null)
            eventHandler = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        return eventHandler;
    }
}

