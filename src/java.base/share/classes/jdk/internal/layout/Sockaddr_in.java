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

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

public final class Sockaddr_in extends Sockaddr {

    private static final ByteOrder NETWORK_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private static final MemoryLayout SOCKADDR_IN_LAYOUT = MemoryLayout.ofStruct(
            MemoryLayouts.BITS_16_LE.withName("sin_family"),
            MemoryLayout.ofValueBits(16, NETWORK_BYTE_ORDER).withName("sin_port"),
            MemoryLayout.ofValueBits(32, NETWORK_BYTE_ORDER).withName("sin_addr"),
            MemoryLayout.ofSequence(8, MemoryLayouts.BITS_8_LE).withName("sin_zero")
    ).withName("sockaddr_in");

    private static final VarHandle SIN_PORT_HANDLE = MemoryHandles.asUnsigned(
            SOCKADDR_IN_LAYOUT.varHandle(short.class, groupElement("sin_port")),
            int.class);
    private static final VarHandle SIN_ADDR_HANDLE = SOCKADDR_IN_LAYOUT.varHandle(int.class, groupElement("sin_addr"));

    public static final int SIZE = (int) SOCKADDR_IN_LAYOUT.byteSize();

    private Sockaddr_in(MemorySegment segment) {
        super(segment, SOCKADDR_IN_LAYOUT);
    }

    public static Sockaddr_in from(Sockaddr sockaddr) {
        if (sockaddr instanceof Sockaddr_in)
            return (Sockaddr_in) sockaddr;
        return new Sockaddr_in(sockaddr.segment);
    }

    public void setFamily() {
        Sockaddr.SA_FAMILY_HANDLE.set(segment.baseAddress(), AF_INET);
    }

    public int port() {
        assert family() == AF_INET;
        return (int) SIN_PORT_HANDLE.get(segment.baseAddress());
    }

    public void setPort(int port) {
        assert family() == AF_INET;
        SIN_PORT_HANDLE.set(segment.baseAddress(), port);
    }

    public byte[] addr() {
        assert family() == AF_INET;
        return intToByteArray((int) SIN_ADDR_HANDLE.get(segment.baseAddress()));
    }

    public void setAddr(int addr) {
        assert family() == AF_INET;
        SIN_ADDR_HANDLE.set(segment.baseAddress(), addr);
    }

    private static final byte[] intToByteArray(int value) {
        return new byte[] { (byte) (value >>> 24),
                            (byte) (value >>> 16),
                            (byte) (value >>> 8),
                            (byte)  value};
    }
}
