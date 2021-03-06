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

import com.cerberustek.worker.TimedWorkerTask;
import com.cerberustek.worker.WorkerPriority;

public abstract class TimedWorkerTaskImpl extends WorkerTaskImpl implements TimedWorkerTask {

    protected final long interval;

    public TimedWorkerTaskImpl(WorkerPriority priority, double interval) {
        super(priority);
        this.interval = (long) (interval * 1e6);
    }

    public TimedWorkerTaskImpl(WorkerPriority priority, long lastFrame, double interval) {
        super(priority, lastFrame);
        this.interval = (long) (interval * 1e6);
    }

    @Override
    public synchronized void execute(long currentTime) {
        double timePassed = (double) (currentTime - lastFrame);
        if (timePassed >= interval) {
            double delta = timePassed * 1e-9;
            execute(delta);
            lastFrame = currentTime;
        }
    }

    @Override
    public int getInterval() {
        return (int) (interval * 1e-6);
    }

    @Override
    public long requestedNextNanos(long currentTime) {
        if (currentTime < lastFrame)
            return interval - (currentTime + Long.MAX_VALUE - lastFrame);
        return interval - (currentTime - lastFrame);
    }

    @Override
    public float getSignificance(long currentTime) {
        long passedTime = currentTime < lastFrame ? (currentTime + Long.MAX_VALUE - lastFrame) : (currentTime - lastFrame);
        if (passedTime < interval)
            return 0;
        else
            return super.getSignificance(currentTime);
    }
}
