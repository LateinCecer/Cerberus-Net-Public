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

package com.cerberustek.worker.impl;

import com.cerberustek.worker.RepeatableTask;
import com.cerberustek.worker.WorkerGroup;
import com.cerberustek.worker.WorkerPriority;
import com.cerberustek.worker.WorkerTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

public class WorkerGroupImpl implements WorkerGroup {

    private final WorkerPriority priority;
    private final HashSet<WorkerTask> tasks = new HashSet<>();
    private final HashSet<WorkerTask> topTasks = new HashSet<>();
    private final String name;

    public WorkerGroupImpl(WorkerPriority priority, String name) {
        this.priority = priority;
        this.name = name;
    }

    @Override
    public void submitTask(WorkerTask task) {
        tasks.add(task);
    }

    @Override
    public void submitTopTask(WorkerTask task) {
        topTasks.add(task);
    }

    @Override
    public void decomissionTask(WorkerTask task) {
        tasks.remove(task);
    }

    @Override
    public void decomissionTopTask(WorkerTask task) {
        topTasks.remove(task);
    }

    @Override
    public void gracefullDecomissionTask(WorkerTask task) {
        if (task instanceof RepeatableTask) {
            if (((RepeatableTask) task).terminate()) {
                tasks.remove(task);
            }
        } else
            tasks.remove(task);
    }

    @Override
    public void gracefullDecomissionTopTask(WorkerTask task) {
        if (task instanceof RepeatableTask) {
            if (((RepeatableTask) task).terminate()) {
                topTasks.remove(task);
            }
        } else
            topTasks.remove(task);
    }

    @Override
    public WorkerPriority getPriority() {
        return priority;
    }

    @Override
    public Collection<WorkerTask> getTasks() {
        return new LinkedHashSet<>(tasks);
    }

    @Override
    public Collection<WorkerTask> getTopTasks() {
        return new LinkedHashSet<>(topTasks);
    }

    @Override
    public boolean hasTopTasks() {
        return !topTasks.isEmpty();
    }

    @Override
    public void clear() {
        tasks.clear();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasNext() {
        return tasks.size() != 0;
    }

    @Override
    public synchronized WorkerTask next() {
        long currentTime = System.nanoTime();
        HashSet<WorkerTask> taskPool = topTasks.isEmpty() ? tasks : topTasks;

        WorkerTask task = null;
        float significance = 0;

        for (WorkerTask t : taskPool) {
            float currentSignificance = t.getSignificance(currentTime);
            if (currentSignificance >= 0 && (task == null || currentSignificance > significance)) {
                task = t;
                significance = currentSignificance;
            }
        }
        return task;
    }

    @Override
    public synchronized long nextRequestedNanos() {
        long currentTime = System.nanoTime();
        long nrm = -1;
        HashSet<WorkerTask> taskPool = topTasks.isEmpty() ? tasks : topTasks;

        for (WorkerTask t : taskPool) {
            long currentNrm = t.requestedNextNanos(currentTime);
            if (currentNrm <= 0)
                return 0;

            if (nrm == -1 || currentNrm < nrm)
                nrm = currentNrm;
        }
        if (nrm == -1)
            nrm = 60000;
        return nrm;
    }

    @Override
    public Iterator<WorkerTask> iterator() {
        return this;
    }

    @Override
    public String toString() {
        return "group: " + super.toString() +
                "\n\t# name: " + getName() +
                "\n\t# tasks: " + getTasks().size() +
                "\n\t# priority: " + getPriority();
    }

    @Override
    public void destroy() {
        tasks.clear();
        topTasks.clear();
    }
}
