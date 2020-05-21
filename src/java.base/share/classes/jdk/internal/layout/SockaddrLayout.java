/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.layout;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;
import static jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement;

public final class SockaddrLayout {

    private static final ByteOrder NETWORK_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final ByteOrder HOST_BYTE_ORDER = ByteOrder.nativeOrder();

    // -- sockaddr

    public static final MemoryLayout sockaddr = MemoryLayout.ofStruct(
            MemoryLayouts.BITS_16_LE.withName("sa_family"),
            MemoryLayout.ofSequence(14, MemoryLayouts.BITS_8_LE).withName("sa_zero")
    ).withName("sockaddr");

    public static final VarHandle SA_FAMILY_HANDLE = sockaddr.varHandle(short.class, groupElement("sa_family"));

    // -- sockaddr_in

    public static final MemoryLayout sockaddr_in = MemoryLayout.ofStruct(
            MemoryLayouts.BITS_16_LE.withName("sin_family"),
            MemoryLayout.ofValueBits(16, NETWORK_BYTE_ORDER).withName("sin_port"),
            MemoryLayout.ofValueBits(16, HOST_BYTE_ORDER).withName("sin_addr"),
            MemoryLayout.ofSequence(8, MemoryLayouts.BITS_8_LE).withName("sin_zero")
    ).withName("sockaddr_in");

    public static final VarHandle SIN_PORT_HANDLE = sockaddr_in.varHandle(short.class, groupElement("sin_port"));
    public static final VarHandle SIN_ADDR_HANDLE = sockaddr_in.varHandle(short.class, groupElement("sin_addr"));

    // -- sockaddr_in6

    public static final MemoryLayout sockaddr_in6 = MemoryLayout.ofStruct(
            MemoryLayouts.BITS_16_LE.withName("sin6_family"),
            MemoryLayout.ofValueBits(16, NETWORK_BYTE_ORDER).withName("sin6_port"),
            MemoryLayout.ofValueBits(32, HOST_BYTE_ORDER).withName("sin6_flowinfo"),
            MemoryLayout.ofSequence(16, MemoryLayouts.JAVA_BYTE).withName("sin6_addr"),
            MemoryLayout.ofValueBits(32, HOST_BYTE_ORDER).withName("sin6_scope_id")
    ).withName("sockaddr_in6");

    public static final VarHandle SIN6_PORT_HANDLE =     sockaddr_in6.varHandle(short.class, groupElement("sin6_port"));
    public static final VarHandle SIN6_FLOWINFO_HANDLE = sockaddr_in6.varHandle(int.class,   groupElement("sin6_flowinfo"));
    public static final VarHandle SIN6_ADDR_HANDLE =     sockaddr_in6.varHandle(byte.class,  groupElement("sin6_addr"), sequenceElement());
    public static final VarHandle SIN6_SCOPE_HANDLE =    sockaddr_in6.varHandle(int.class,   groupElement("sin6_scope_id"));
    public static final long SIN6_ADDR_OFFSET = sockaddr_in6.byteOffset(groupElement("sin6_addr"));
    public static final long SIN6_ADDR_BYTES_LENGTH = 16;

}