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

package com.cerberustek.alexandria.collection;

import com.cerberustek.CerberusRegistry;
import com.cerberustek.settings.Settings;
import com.cerberustek.settings.impl.SettingsImpl;
import com.cerberustek.exception.UnknownDiscriminatorException;
import com.cerberustek.utils.DiscriminatorFile;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class BatchInfo implements Closeable {

    private static String INFO_PATH = "batches/";

    private final String name;
    private final Settings settings;

    public static BatchInfo readBatchInfo(String name) {
        File file = new File(INFO_PATH + name);
        if (!file.exists() || !file.isFile())
            return null;

        return new BatchInfo(INFO_PATH + name);
    }

    public static BatchInfo createBatchInfo(String name, Class<? extends Batch> batchClass, String discriminatorFile) {
        File file = new File(INFO_PATH + name);
        if (file.exists() && file.isFile())
            throw new IllegalStateException("Batch info with that name does already exist.");

        BatchInfo info = new BatchInfo(INFO_PATH + name);
        info.settings.setString("class", batchClass.toString());
        if (discriminatorFile != null)
            info.settings.setString("discriminators", discriminatorFile);

        return info;
    }

    public static void setInfoPath(String path) {
        INFO_PATH = path;
    }

    public static String getInfoPath() {
        return INFO_PATH;
    }

    private BatchInfo(String name) {
        this.name = name;
        settings = new SettingsImpl(new File(INFO_PATH + name));
    }

    public String getName() {
        return name;
    }

    public DiscriminatorFile getDiscriminatorFile() {
        File file = new File(settings.getString("discriminators", "default.mdf"));
        if (file.exists())
            return new DiscriminatorFile(file);

        DiscriminatorFile output = new DiscriminatorFile(file);
        try {
            output.writeDefault();
        } catch (IOException | UnknownDiscriminatorException e) {
            CerberusRegistry.getInstance().warning("Unable to write default discriminators! Cause: " + e);
            return null;
        }
        return output;
    }

    public Class<? extends Batch> getBatchClass() {
        String className = settings.getString("class", "de.cerberus.collection.impl.LocalBatch");
        try {
            ClassLoader loader = getClass().getClassLoader();

            Class clazz = loader.loadClass(className);
            if (clazz.getSuperclass().equals(Batch.class))
                //noinspection unchecked
                return clazz;
        } catch (ClassNotFoundException e) {
            CerberusRegistry.getInstance().debug("Unable to load batch class " + className + ". Missing extension?");
        }
        return null;
    }

    public Batch loadBatch() throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {

        Class<? extends Batch> clazz = getBatchClass();
        if (clazz == null)
            return null;

        Constructor<? extends Batch> constructor = clazz.getConstructor(BatchInfo.class);
        if (constructor == null) {
            CerberusRegistry.getInstance().debug("Unable to load batch constructor for batch class " + clazz.toString());
            return null;
        }

        return constructor.newInstance(this);
    }

    @Override
    public void close() throws IOException {
        settings.destroy();
    }

    public Settings getSettings() {
        return settings;
    }
}
