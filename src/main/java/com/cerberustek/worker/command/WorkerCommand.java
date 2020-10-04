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

package com.cerberustek.worker.command;

import com.cerberustek.CerberusRegistry;
import com.cerberustek.service.TerminalUtil;
import com.cerberustek.service.terminal.TerminalCommand;
import com.cerberustek.usr.PermissionHolder;
import com.cerberustek.worker.WorkerBoss;

import java.util.HashSet;
import java.util.Scanner;

public class WorkerCommand implements TerminalCommand {

    private final HashSet<WorkerBoss> bossSet = new HashSet<>();

    private static WorkerCommand instance;
    private WorkerCommand() {}

    public static WorkerCommand getInstance() {
        if (instance == null)
            instance = new WorkerCommand();
        return instance;
    }

    public void addWorkerBoss(WorkerBoss boss) {
        bossSet.add(boss);
    }

    public void removeWorkerBoss(WorkerBoss boss) {
        bossSet.remove(boss);
    }

    @Override
    public boolean execute(PermissionHolder permissionHolder, Scanner scanner, String... strings) {
        if (strings.length > 0) {
            CerberusRegistry registry = CerberusRegistry.getInstance();

            switch (strings[0].toLowerCase()) {
                case "list":
                    registry.info(TerminalUtil.ANSI_YELLOW + "Here is a list of all registered boss workers:"
                            + TerminalUtil.ANSI_RESET + "\n");
                    int counter = 0;
                    for (WorkerBoss boss : bossSet) {
                        registry.info(TerminalUtil.ANSI_CYAN + "# " + TerminalUtil.ANSI_RESET
                                + "[" + counter + "]:");
                        registry.info(boss.toString());
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String executor() {
        return "worker";
    }

    @Override
    public String usage() {
        return "worker <list>";
    }

    @Override
    public String requiredPermission() {
        return "de.cerberus.worker";
    }
}
