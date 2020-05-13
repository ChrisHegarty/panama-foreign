/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.incubator.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

final class MemoryHandleUnsignedByteFromLong {
    private static final MethodHandle TO_TARGET;
    private static final MethodHandle FROM_TARGET;

    static {
        try {
            TO_TARGET = MethodHandles.lookup().findStatic(MemoryHandleUnsignedByteFromLong.class, "toTarget",
                    MethodType.methodType(byte.class, long.class));
            FROM_TARGET = MethodHandles.lookup().findStatic(MemoryHandleUnsignedByteFromLong.class, "fromTarget",
                    MethodType.methodType(long.class, byte.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private MemoryHandleUnsignedByteFromLong() { } // no instances

    static VarHandle from(VarHandle target) {
        checkCarrierType(target.varType());
        return MemoryHandles.filterValue(target, TO_TARGET, FROM_TARGET);
    }

    private static byte toTarget(long value) {
        return (byte) value;
    }

    private static long fromTarget(byte value) {
        return value & 0xFFL;
    }

    private static final Class<?> CARRIER_TYPE = byte.class;

    private static void checkCarrierType(Class<?> carrier) {
        if (carrier != CARRIER_TYPE)
            throw new InternalError("expected %s carrier, but got %s".formatted(CARRIER_TYPE, carrier));
    }
}
