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

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.internal.misc.Unsafe;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;
import static jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement;

public final class Sockaddr_in6 extends Sockaddr {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private Sockaddr_in6(MemorySegment segment) {
        super(segment, LAYOUT);
    }

    public static Sockaddr_in6 allocate() {
        long size = sockaddr_in6.byteSize();
        long base = UNSAFE.allocateMemory(size);
        var segment = MemorySegment.ofNativeRestricted(MemoryAddress.ofLong(base), size, null, () -> UNSAFE.freeMemory(base), null);
        segment.fill((byte) 0x00);
        return new Sockaddr_in6(segment);
    }

    public static Sockaddr_in6 from(Sockaddr sockaddr) {
        if (sockaddr instanceof Sockaddr_in6)
            return (Sockaddr_in6) sockaddr;
        return new Sockaddr_in6(sockaddr.segment);
    }

    // -- family

    public void setFamily() {
        Sockaddr.SA_FAMILY_HANDLE.set(segment.baseAddress(), AF_INET6);
    }

    // -- port

    public int port() {
        return (int) SIN6_PORT_HANDLE.get(segment.baseAddress());
    }

    public void setPort(int port) {
        SIN6_PORT_HANDLE.set(segment.baseAddress(), port);
    }

    // -- addr

    public byte[] addr() {
        assert family() == AF_INET6;
        return getAFINET6Address(segment.asSlice(SIN6_ADDR_OFFSET,
                                                 SIN6_ADDR_BYTES_LENGTH));
    }

    private static byte[] getAFINET6Address(MemorySegment segment) {
        byte[] ba = new byte[16];
        MemorySegment seg = MemorySegment.ofArray(ba);
        seg.copyFrom(segment);
        return ba;
    }

    public void setAddr(byte[] ba) {
        assert family() == AF_INET6;
        var addrSeg = segment.asSlice(SIN6_ADDR_OFFSET,
                                      SIN6_ADDR_BYTES_LENGTH);
        addrSeg.copyFrom(MemorySegment.ofArray(ba));
    }
    
    // -- scope
    
    public int getScopeId() {
        return (int) SIN6_SCOPE_HANDLE.get(segment.baseAddress());
    }

    public void setScopeId(int scopeId) {
        SIN6_SCOPE_HANDLE.set(segment.baseAddress(), scopeId);
    }

    // -- flow

    public int getFlowInfo() {
        return (int) SIN6_FLOWINFO_HANDLE.get(segment.baseAddress());
    }

    public void setFlowInfo(int flowInfo) {
        SIN6_FLOWINFO_HANDLE.set(segment.baseAddress(), flowInfo);
    }

    // --
    private static final ByteOrder NETWORK_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final ByteOrder HOST_BYTE_ORDER = ByteOrder.nativeOrder();

    private static final MemoryLayout sockaddr_in6 = MemoryLayout.ofStruct(
            MemoryLayouts.BITS_16_LE.withName("sin6_family"),
            MemoryLayout.ofValueBits(16, NETWORK_BYTE_ORDER).withName("sin6_port"),
            MemoryLayout.ofValueBits(32, HOST_BYTE_ORDER).withName("sin6_flowinfo"),
            MemoryLayout.ofSequence(16, MemoryLayouts.JAVA_BYTE).withName("sin6_addr"),
            MemoryLayout.ofValueBits(32, HOST_BYTE_ORDER).withName("sin6_scope_id")
    ).withName("sockaddr_in6");

    private static final VarHandle SIN6_PORT_HANDLE = MemoryHandles.asUnsigned(
            sockaddr_in6.varHandle(short.class, groupElement("sin6_port")),
            int.class);
    private static final VarHandle SIN6_FLOWINFO_HANDLE = sockaddr_in6.varHandle(int.class,   groupElement("sin6_flowinfo"));
    private static final VarHandle SIN6_ADDR_HANDLE =     sockaddr_in6.varHandle(byte.class,  groupElement("sin6_addr"), sequenceElement());
    private static final VarHandle SIN6_SCOPE_HANDLE =    sockaddr_in6.varHandle(int.class,   groupElement("sin6_scope_id"));
    private static final long SIN6_ADDR_OFFSET = sockaddr_in6.byteOffset(groupElement("sin6_addr"));
    private static final long SIN6_ADDR_BYTES_LENGTH = 16;

    private static final MemoryLayout LAYOUT = sockaddr_in6;

    public static final int SIZE = (int) LAYOUT.byteSize();
}
