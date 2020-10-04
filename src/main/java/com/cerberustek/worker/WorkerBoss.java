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

import java.io.PrintStream;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface WorkerBoss {

    void changeStatus(WorkerStatus status) throws IllegalStateException;

    void submitWorker(Worker worker);
    void decomissionWorker(Worker worker);

    void submitGroup(String name, WorkerGroup group);
    void decomissionGroup(String groupName);

    WorkerTask submitTask(WorkerTask task, String groupName);
    WorkerTask submitTopTask(WorkerTask task, String groupName);

    WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName);
    WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay);
    WorkerTask submitTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay);
    WorkerTask submitTask(Consumer<Double> consumer, String groupName);
    WorkerTask submitTask(Consumer<Double> consumer, String groupName, int delay);
    WorkerTask submitTask(Consumer<Double> consumer, String groupName, double delay);

    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions);
    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions, int delay);
    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions, double delay);
    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions);
    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay);
    WorkerTask submitTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay);

    WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName);
    WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay);
    WorkerTask submitTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay);
    WorkerTask submitTopTask(Consumer<Double> consumer, String groupName);
    WorkerTask submitTopTask(Consumer<Double> consumer, String groupName, int delay);
    WorkerTask submitTopTask(Consumer<Double> consumer, String groupName, double delay);

    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                             int repetitions);
    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                             int repetitions, int delay);
    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                             int repetitions, double delay);
    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions);
    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay);
    WorkerTask submitTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay);

    WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName);
    WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay);
    WorkerTask submitWaitingTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay);
    WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName);
    WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName, int delay);
    WorkerTask submitWaitingTask(Consumer<Double> consumer, String groupName, double delay);

    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions);
    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions, int delay);
    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                          int repetitions, double delay);
    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions);
    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay);
    WorkerTask submitWaitingTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay);

    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName);
    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int delay);
    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, double delay);
    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName);
    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName, int delay);
    WorkerTask submitWaitingTopTask(Consumer<Double> consumer, String groupName, double delay);

    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                                    int repetitions);
    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                                    int repetitions, int delay);
    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                                    int repetitions, double delay);
    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions);
    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int delay);
    WorkerTask submitWaitingTopTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, double delay);

    /*
    WorkerTask submitPrecisionTask(Consumer<Double> consumer, WorkerPriority priority, String groupName, int millis,
                                   int nanos);
    WorkerTask submitPrecisionTask(Consumer<Double> consumer, String groupName, int millis, int nanos);
    WorkerTask submitPrecisionTask(BiConsumer<Double, Integer> consumer, WorkerPriority priority, String groupName,
                                   int repetitions, int millis, int nanos);
    WorkerTask submitPrecisionTask(BiConsumer<Double, Integer> consumer, String groupName, int repetitions, int millis,
                                   int nanos);*/

    void decomissionTask(WorkerTask task, String group);

    WorkerGroup getGroup(String groupName);
    WorkerGroup createGroup(String name, WorkerPriority priority);

    void clearGroup(String groupName);

    Worker createWorker(WorkerPriority priority, String... groups);
    Collection<Worker> getWorkers();
    Collection<Thread> getThreads();

    WorkerStatus status();

    void print(PrintStream stream);
}
