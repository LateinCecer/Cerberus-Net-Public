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

import com.cerberustek.alexandria.collection.Batch;
import com.cerberustek.service.CerberusService;
import com.cerberustek.alexandria.client.AlexandriaClient;
import com.cerberustek.worker.WorkerBoss;

import java.net.InetAddress;
import java.util.Collection;

public class AlexandriaClientImpl implements AlexandriaClient {
    
    @Override
    public boolean hasTCP() {
        return false;
    }

    @Override
    public boolean hasUDP() {
        return false;
    }

    @Override
    public WorkerBoss getWorkerBoss() {
        return null;
    }

    @Override
    public Batch openBatch(InetAddress db, String batch) {
        return null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Class<? extends CerberusService> serviceClass() {
        return null;
    }

    @Override
    public Collection<Thread> getThreads() {
        return null;
    }
}
