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

package com.cerberustek.alexandria.bootstrap;

import com.cerberustek.CerberusRegistry;
import com.cerberustek.event.impl.CerberusEventImpl;
import com.cerberustek.CerberusNet;
import com.cerberustek.alexandria.server.impl.AlexandriaServerImpl;

public class ServerBootstrap {

    public static void main(String... args) {
        CerberusRegistry registry = CerberusRegistry.getInstance();
        registry.info("Init Alexandria server bootstrap...");

        registry.registerService(new CerberusEventImpl());
        registry.registerService(new CerberusNet());
        registry.registerService(new AlexandriaServerImpl());

        registry.requestStart();
    }
}
