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

package com.cerberustek.udp;

import com.cerberustek.CerberusEvent;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.channel.NetPipeline;
import com.cerberustek.channel.NetValve;
import com.cerberustek.events.NetValveStartEvent;
import com.cerberustek.valve.NetValveBase;
import com.cerberustek.worker.WorkerBoss;
import com.cerberustek.worker.WorkerPriority;

public class UDPValve extends NetValveBase implements NetValve {

    public UDPValve(NetPipeline pipeline, int bufferCapacity) {
        super(pipeline, bufferCapacity);
    }

    @Override
    public void start(WorkerBoss boss, String workerGroup, WorkerPriority priority) {
        this.boss = boss;
        this.group = workerGroup;
        this.priority = priority;

        CerberusRegistry.getInstance().getService(CerberusEvent.class).executeFullEIF(new NetValveStartEvent(this));
    }

    /*
    @Override
    public void updateInputs() {
        if (task == null && boss != null) {
            task = boss.submitTask((delta) -> {
                super.updateInputs();
                task = null;
            }, priority, group);
        }
    }*/
}
