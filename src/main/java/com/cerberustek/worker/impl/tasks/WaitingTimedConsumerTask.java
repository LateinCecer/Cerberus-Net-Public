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

package com.cerberustek.worker.impl.tasks;

import com.cerberustek.worker.WaiterTask;
import com.cerberustek.worker.WorkerPriority;

import java.util.function.Consumer;

public class WaitingTimedConsumerTask extends TimedConsumerTask implements WaiterTask {

    private boolean hasRun;

    public WaitingTimedConsumerTask(WorkerPriority priority, double intervall, Consumer<Double> consumer) {
        super(priority, intervall, consumer);
        hasRun = false;
    }

    public WaitingTimedConsumerTask(WorkerPriority priority, long lastFrame, double intervall, Consumer<Double> consumer) {
        super(priority, lastFrame, intervall, consumer);
        hasRun = false;
    }

    @Override
    public synchronized void execute(double deltaT) {
        super.execute(deltaT);
        hasRun = true;
        this.notifyAll();
    }

    @Override
    public void hold() throws InterruptedException {
        if (!hasRun) {
            synchronized (this) {
                this.wait();
            }
        }
    }

    @Override
    public void hold(int millis) throws InterruptedException {
        if (!hasRun) {
            synchronized (this) {
                this.wait(millis);
            }
        }
    }

    @Override
    public void reset() {
        hasRun = false;
    }
}
