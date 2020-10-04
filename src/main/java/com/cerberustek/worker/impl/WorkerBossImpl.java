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
import com.cerberustek.exceptions.WorkerOutOfSyncException;
import com.cerberustek.service.impl.MainService;
import com.cerberustek.service.terminal.Terminal;
import com.cerberustek.worker.*;
import com.cerberustek.worker.command.WorkerCommand;
import com.cerberustek.worker.impl.tasks.*;

import java.io.PrintStream;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WorkerBossImpl implements WorkerBoss {

    private final HashSet<Worker> workers = new HashSet<>();
    private final HashMap<String, WorkerGroup> groups = new HashMap<>();

    public WorkerBossImpl() {
        Terminal terminal = CerberusRegistry.getInstance().getService(MainService.class).getTerminal();
        terminal.getExecutor().registerCommand(WorkerCommand.getInstance());
        WorkerCommand.getInstance().addWorkerBoss(this);
    }

    @Override
    public void changeStatus(WorkerStatus status) throws IllegalStateException {
        for (Worker worker : workers)
            worker.changeStatus(status);
    }

    @Override
    public void submitWorker(Worker worker) {
        if (worker == null)
            return;
        workers.add(worker);
    }

    @Override
    public void decomissionWorker(Worker worker) {
        if (worker == null)
            return;

        if (worker.status() != WorkerStatus.TERMINATING && worker.status() != WorkerStatus.TERMINATED)
            worker.changeStatus(WorkerStatus.TERMINATING);
        workers.remove(worker);
    }

    @Override
    public void submitGroup(String name, WorkerGroup group) {
        if (name == null || group == null)
            return;

        if (!groups.containsKey(name))
            groups.put(name, group);
    }

    @Override
    public WorkerTask submitTask(WorkerTask task, String groupName) {
        WorkerGroup group = groups.get(groupName);
        if (group != null) {
            group.submitTask(task);
            for (Worker worker : workers) {
                if (worker.status().equals(WorkerStatus.SLEEPING) && worker.isInGroup(group)) {
                    try {
                        worker.changeStatus(WorkerStatus.ACTIVE);
                    } catch (IllegalStateException e) {
                        // Ignore
                    }
                }
            }
            return task;
        }
        return null;
    }

    @Override
    public WorkerTask submitTopTask(WorkerTask task, String groupName) {
        WorkerGroup group = groups.get(groupName);
        if (group != null) {
            group.submitTopTask(task);
            for (Worker worker : workers) {
                if (worker.status().equals(WorkerStatus.SLEEPING) && worker.isInGroup(group)) {
                    try {
                        worker.changeStatus(WorkerStatus.ACTIVE);
                    } catch (IllegalStateException e) {
                        // ignore
                    }
                }
            }
            return task;
        }
        return null;
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName) {
        return submitTask(new ConsumerTask(priority, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay) {
        return submitTask(new TimedConsumerTask(priority, System.nanoTime(), delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay) {
        return submitTask(new TimedConsumerTask(priority, System.nanoTime(), delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, String groupName) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName);
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, String groupName, int delay) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitTask(Consumer<Double> consumer, String groupName, double delay) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions) {
        return submitTask(new RepeatableConsumerTask(priority, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, int delay) {
        return submitTask(new RepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, double delay) {
        return submitTask(new RepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay) {
        return submitTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName) {
        return submitTopTask(new ConsumerTask(priority, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay) {
        return submitTopTask(new TimedConsumerTask(priority, delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay) {
        return submitTopTask(new TimedConsumerTask(priority, delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, String groupName) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, String groupName, int delay) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitTopTask(Consumer<Double> consumer, String groupName, double delay) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions) {
        return submitTopTask(new RepeatableConsumerTask(priority, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, int delay) {
        return submitTopTask(new RepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, double delay) {
        return submitTopTask(new RepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay) {
        return submitTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName) {
        return submitTask(new WaitingConsumerTask(priority, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay) {
        return submitTask(new WaitingTimedConsumerTask(priority, System.nanoTime(), delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay) {
        return submitTask(new WaitingTimedConsumerTask(priority, System.nanoTime(), delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName, int delay) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName, double delay) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions) {
        return submitTask(new WaitingRepeatableConsumerTask(priority, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, int delay) {
        return submitTask(new WaitingRepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, double delay) {
        return submitTask(new WaitingRepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay) {
        return submitWaitingTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName) {
        return submitTopTask(new WaitingConsumerTask(priority, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay) {
        return submitTopTask(new WaitingTimedConsumerTask(priority, delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay) {
        return submitTopTask(new WaitingTimedConsumerTask(priority, delay, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName, int delay) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName, double delay) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName, delay);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions) {
        return submitTopTask(new WaitingRepeatableConsumerTask(priority, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, int delay) {
        return submitTopTask(new WaitingRepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName, int repetitions, double delay) {
        return submitTopTask(new WaitingRepeatableTimedConsumerTask(priority, delay, repetitions, consumer), groupName);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay) {
        return submitWaitingTopTask(consumer, WorkerPriority.MEDIUM, groupName, repetitions, delay);
    }

    @Override
    public void decomissionTask(WorkerTask task, String groupName) {
        if (task == null || groupName == null)
            return;

        WorkerGroup group = groups.get(groupName);
        if (group != null)
            group.decomissionTask(task);
    }

    @Override
    public void decomissionGroup(String groupName) {
        if (groupName == null)
            return;

        groups.remove(groupName);
    }

    @Override
    public WorkerGroup getGroup(String groupName) {
        return groups.get(groupName);
    }

    @Override
    public WorkerGroup createGroup(String name, WorkerPriority priority) {
        WorkerGroup group = getGroup(name);
        if (group == null) {
            group = new WorkerGroupImpl(priority, name);
            groups.put(name, group);
        }
        return group;
    }

    @Override
    public void clearGroup(String groupName) {
        WorkerGroup group = getGroup(groupName);
        if (group != null)
            group.clear();
    }

    @Override
    public Worker createWorker(WorkerPriority priority, String... groups) {
        WorkerGroup[] g = new WorkerGroup[groups.length];
        for (int i = 0; i < g.length; i++) {
            WorkerGroup gs = getGroup(groups[i]);
            if (gs != null)
                g[i] = gs;
            else
                throw new NullPointerException("Missing group with name: " + groups[i] + "!");
        }
        Worker worker = new WorkerImpl(priority, g);
        submitWorker(worker);
        return worker;
    }

    @Override
    public Collection<Worker> getWorkers() {
        return workers;
    }

    @Override
    public Collection<Thread> getThreads() {
        HashSet<Thread> output = new HashSet<>();
        for (Worker worker : workers)
            output.add(worker.thread());
        return output;
    }

    @Override
    public WorkerStatus status() {
        WorkerStatus status = null;
        for (Worker worker : workers) {
            if (status == null)
                status = worker.status();
            else if (!worker.status().equals(status))
                throw new WorkerOutOfSyncException();
        }
        return status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Workers: ").append(workers.size());
        for (Worker worker : workers)
            builder.append('\n').append('\t').append(worker.toString().replace("\n", "\n\t"));
        return builder.toString();
    }

    @Override
    public void print(PrintStream stream) {
        stream.println("Workers: " + workers.size());
        for (Worker worker : workers) {
            String[] s = worker.toString().split("\n");
            for (String line : s)
                stream.println("\t" + line);
        }
    }
}
