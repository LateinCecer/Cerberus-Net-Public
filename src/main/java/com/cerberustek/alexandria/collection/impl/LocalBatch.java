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

package com.cerberustek.alexandria.collection.impl;

import com.cerberustek.CerberusData;
import com.cerberustek.CerberusRegistry;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.MetaInputStream;
import com.cerberustek.data.MetaOutputStream;
import com.cerberustek.data.impl.elements.CipherElement;
import com.cerberustek.data.impl.tags.CipherTag;
import com.cerberustek.alexandria.collection.Batch;
import com.cerberustek.cipher.CerberusCipher;
import com.cerberustek.cipher.impl.FenrirCipher;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.exception.ResourceUnavailableException;
import com.cerberustek.exception.UnknownDiscriminatorException;
import com.cerberustek.querry.QueryResult;
import com.cerberustek.querry.ResourceLocation;
import com.cerberustek.querry.trace.QueryTrace;

import java.io.*;

public class LocalBatch implements Batch {

    private final File file;

    private String passwd;
    private MetaData data;
    private DiscriminatorMap discriminatorMap;

    public LocalBatch(String path) throws FileNotFoundException {
        file = new File(path);
        if (!file.exists() || !file.isFile())
            throw new FileNotFoundException("Could not open file: " + path + "!");
    }

    @Override
    public QueryResult query(QueryTrace trace) throws ResourceUnavailableException {
        if (data == null || !(data instanceof ResourceLocation))
            return null;
        return ((ResourceLocation) data).trace(trace);
    }

    @Override
    public void save() throws IOException, NoMatchingDiscriminatorException {
        prepareDiscriminators(discriminatorMap, passwd);
        MetaOutputStream outputStream = CerberusData.createOutputStream(new FileOutputStream(file), discriminatorMap);

        outputStream.writeData(data);
        outputStream.flush();
        outputStream.close();
    }

    @Override
    public boolean open(String password, DiscriminatorMap discriminatorMap) throws
            FileNotFoundException, IllegalAccessException {

        if (data != null) {
            if (password.equals(passwd))
                return true;
            throw new IllegalAccessException("Wrong password " + password + "!");
        }

        try {
            prepareDiscriminators(discriminatorMap, password);

            MetaInputStream inputStream = CerberusData.createInputStream(new FileInputStream(file), discriminatorMap);
            data = inputStream.readData();
            passwd = password;
            this.discriminatorMap = discriminatorMap;

            inputStream.close();
            return true;
        } catch (FileNotFoundException e) {
            throw e;
        } catch (UnknownDiscriminatorException | IOException e) {
            throw new IllegalAccessException("Failed to read or decipher batch file!");
        }
    }

    private static DiscriminatorMap prepareDiscriminators(DiscriminatorMap discriminatorMap, String passwd) {
        discriminatorMap.unregisterData(CipherElement.class);
        discriminatorMap.unregisterData(CipherTag.class);

        CerberusCipher cipher = new FenrirCipher(passwd.getBytes());

        return discriminatorMap;
    }

    @Override
    public void close() {
        try {
            save();
        } catch (IOException | NoMatchingDiscriminatorException e) {
            CerberusRegistry.getInstance().warning("Failed to save batch " + this + ". Cause: " + e);
        }
        data = null;
    }
}
