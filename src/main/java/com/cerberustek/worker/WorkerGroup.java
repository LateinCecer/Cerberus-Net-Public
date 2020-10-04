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

package com.cerberustek.worker;

import com.cerberustek.Destroyable;

import java.util.Collection;
import java.util.Iterator;

public interface WorkerGroup extends Iterator<WorkerTask>, Iterable<WorkerTask>, Destroyable {

    /**
     * Will submit a normal task to the worker group.
     * @param task normal task
     */
    void submitTask(WorkerTask task);

    /**
     * Will submit a top task to the worker group.
     *
     * A top task will always to executed before normal tasks
     * are executed. Thus, no normal task will be run as long
     * as there is a top task in the group.
     *
     * @param task top task to add
     */
    void submitTopTask(WorkerTask task);

    /**
     * Will decomission a normal task from the group.
     * @param task normal task
     */
    void decomissionTask(WorkerTask task);

    /**
     * Will decomission a top task from the group.
     * @param task top task
     */
    void decomissionTopTask(WorkerTask task);

    /**
     * Will decomission a task after it's current run is
     * finished.
     * @param task task to decomission
     */
    void gracefullDecomissionTask(WorkerTask task);

    /**
     * Will decomission a top task after it's current run is
     * finished.
     * @param task task to decomission
     */
    void gracefullDecomissionTopTask(WorkerTask task);

    /**
     * Returns the significance if the worker group.
     * @return group significance
     */
    default float getSignificance() {
        return (float) Math.pow((float) getTasks().size(), getPriority().weight);
    }

    /**
     * Returns the amount of nano seconds after which the group
     * has a new task to run.
     * @return next nanos
     */
    long nextRequestedNanos();

    WorkerPriority getPriority();

    Collection<WorkerTask> getTasks();
    Collection<WorkerTask> getTopTasks();

    boolean hasTopTasks();

    void clear();

    String getName();
}
