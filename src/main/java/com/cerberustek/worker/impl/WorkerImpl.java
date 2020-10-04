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

import com.cerberustek.CerberusRegistry;
import com.cerberustek.service.TerminalUtil;
import com.cerberustek.worker.*;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.UUID;

public class WorkerImpl implements Worker {

    private final WorkerPriority priority;
    private final WorkerGroup[] groups;
    private WorkerStatus status = WorkerStatus.TERMINATED;
    private WorkerThread thread;

    private long startTime = 0;
    private long computations = 0;

    public WorkerImpl(WorkerPriority priority, WorkerGroup[] groups) {
        this.priority = priority;
        this.groups = groups;
    }

    @Override
    public void changeStatus(WorkerStatus status) throws IllegalStateException {
        try {
            if (!this.status.equals(status)) {

                if (this.status == WorkerStatus.STARTING)
                    throw new IllegalStateException("Worker is starting!");
                if (this.status == WorkerStatus.TERMINATING)
                    throw new IllegalStateException("Worker is terminating!");

                switch (status) {
                    case ACTIVE:
                        if (!this.status.equals(WorkerStatus.SLEEPING))
                            throw new IllegalStateException("Worker cannot be set to ACTIVE. Try STARTING to start the worker!");
                        else if (thread != null) {
                            //noinspection SynchronizeOnNonFinalField
                            synchronized (thread) {
                                thread.notify();
                            }
                        }
                        return;
                    case STARTING:
                        if (this.status.equals(WorkerStatus.TERMINATED)) {
                            thread = new WorkerThread();
                            thread.start();
                        } else
                            throw new IllegalStateException("Worker can only be started while it is terminated!");
                        break;
                    case SLEEPING:
                        if (!this.status.equals(WorkerStatus.ACTIVE))
                            throw new IllegalStateException("Worker can only be put to sleep while it is active!");
                        break;
                    case TERMINATED:
                        throw new IllegalStateException("Worker cannot be set to TERMINATED. Try TERMINATING to terminate the worker!");
                    case TERMINATING:
                        if (this.status == WorkerStatus.SLEEPING && thread != null) {
                            this.status = status;
                            //noinspection SynchronizeOnNonFinalField
                            synchronized (thread) {
                                thread.notify();
                            }
                        }
                        break;
                }

                this.status = status;
            }
        } catch (Exception e) {
            CerberusRegistry.getInstance().critical("dsflkjdaslkjsdf ");
            e.printStackTrace();
        }
    }

    @Override
    public float computationsPerSecond(long currentTime) {
        return (float) ((double) computations() / (double) (currentTime - startTime));
    }

    @Override
    public long startTime() {
        return startTime;
    }

    @Override
    public long computations() {
        return computations;
    }

    @Override
    public Thread thread() {
        return thread;
    }

    @Override
    public WorkerStatus status() {
        return status;
    }

    @Override
    public WorkerPriority getPriority() {
        return priority;
    }

    @Override
    public WorkerGroup[] getGroups() {
        return groups;
    }

    @Override
    public boolean hasActiveGroup() {
        for (WorkerGroup group : groups) {
            if (!group.getTasks().isEmpty())
                return true;
        }
        return false;
    }

    @Override
    public boolean isInGroup(WorkerGroup group) {
        for (WorkerGroup g : groups) {
            if (g.equals(group))
                return true;
        }
        return false;
    }

    private class WorkerThread extends Thread {

        public WorkerThread() {
            super("Worker, " + priority.name() + ", " + Arrays.toString(groups) + UUID.randomUUID().toString());
        }

        @Override
        public void run() {
            try {

                startTime = System.currentTimeMillis();
                computations = 0;
                status = WorkerStatus.ACTIVE;
                while (!status.equals(WorkerStatus.TERMINATING)) {
                    if (status.equals(WorkerStatus.SLEEPING)) {
                        synchronized (this) {
                            try {
                                this.wait(1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                if (status != WorkerStatus.TERMINATING)
                                    status = WorkerStatus.ACTIVE;
                            }
                        }
                    }

                    try {
                        WorkerGroup group = mostSignificantGroup();
                        WorkerTask task;
                        if (group != null)
                            task = group.next();
                        else
                            task = null;

                        if (task != null) {
                            long currentTime = System.nanoTime();
                            long rne = task.requestedNextNanos(currentTime);

                            if (rne <= 0) {
                                task.execute(currentTime);

                                if (group.hasTopTasks())
                                    group.gracefullDecomissionTopTask(task);
                                else
                                    group.gracefullDecomissionTask(task);
                                computations++;
                            } else {
                                rne = group.nextRequestedNanos();

                                if (rne / 1000000L > 0 && status != WorkerStatus.TERMINATING) {
                                    status = WorkerStatus.SLEEPING;
                                    synchronized (this) {
                                        try {
                                            this.wait(rne / 1000000L);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (status != WorkerStatus.TERMINATING)
                                                status = WorkerStatus.ACTIVE;
                                        }
                                    }
                                }
                            }
                        } else if (!hasActiveGroup() && status == WorkerStatus.ACTIVE)
                            changeStatus(WorkerStatus.SLEEPING);
                    } catch (ConcurrentModificationException e) {
                        // Generic error. Ignore!
                    }
                }
                status = WorkerStatus.TERMINATED;


            } catch (Exception e) {
                CerberusRegistry.getInstance().critical("Caught exception in worker base:");
                e.printStackTrace();
            }
        }

        private WorkerGroup mostSignificantGroup() {
            WorkerGroup currentGroup = null;
            float significance = 0;
            for (WorkerGroup group : groups) {
                float currentSignificance = group.getSignificance();
                if (currentGroup == null || currentSignificance > significance) {
                    significance = currentSignificance;
                    currentGroup = group;
                }
            }
            return currentGroup;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Worker: ").append(super.toString());
        builder.append("\n\t# status: ").append(status);
        builder.append("\n\t# active: ").append(hasActiveGroup());
        builder.append("\n\t# priority: ").append(priority);
        builder.append("\n\t# start time: ").append(TerminalUtil.getInstance().formatTime(startTime));
        builder.append("\n\t# computations: ").append(computations);
        builder.append("\n\t# groups: ");
        for (WorkerGroup group : getGroups())
            builder.append('\n').append('\t').append('\t')
                    .append(group.toString().replace("\n", "\n\t\t"));
        if (status == WorkerStatus.ACTIVE) {
            WorkerGroup most = thread.mostSignificantGroup();
            if (most != null)
                builder.append("\n\t# most significant: ").append(most.getName());
            builder.append("\n\t# thread: ").append(thread);
        }
        return builder.toString();
    }
}
