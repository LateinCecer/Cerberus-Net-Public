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

import com.cerberustek.service.TerminalUtil;

public enum WorkerPriority {

    LOW(0.5f),
    MEDIUM(1.0f),
    HIGH(1.5f),
    ABSOLUTE(2f);

    public final float weight;

    WorkerPriority(float weight) {
        this.weight = weight;
    }


    @Override
    public String toString() {
        switch (this) {
            case LOW:
                return TerminalUtil.ANSI_GREEN + "LOW" + TerminalUtil.ANSI_RESET;
            case MEDIUM:
                return TerminalUtil.ANSI_YELLOW + "MEDIUM" + TerminalUtil.ANSI_RESET;
            case HIGH:
                return TerminalUtil.ANSI_PURPLE + "HIGH" + TerminalUtil.ANSI_RESET;
            case ABSOLUTE:
                return TerminalUtil.ANSI_RED + "ABSOLUTE" + TerminalUtil.ANSI_RESET;
            default:
                return "Unknown";
        }
    }
}
