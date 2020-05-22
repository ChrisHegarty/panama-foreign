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
import sun.nio.ch.NativeSocketAddress;
import java.lang.invoke.VarHandle;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

public abstract class Sockaddr {

    // ####: move these
    public static final int AF_INET = NativeSocketAddress.AFINET();
    public static final int AF_INET6 = NativeSocketAddress.AFINET6();

    public static final MemoryLayout SOCKADDR_LAYOUT;
    protected static final VarHandle SA_FAMILY_HANDLE;

    static {
        // platform specific layout
        Class<?> carrier;
        if (NativeSocketAddress.sizeofFamily() == 1) {
            SOCKADDR_LAYOUT =  MemoryLayout.ofStruct(
                    MemoryLayout.ofPaddingBits(8),
                    MemoryLayouts.JAVA_BYTE.withName("sa_family")
            ).withName("sockaddr");
            carrier = byte.class;
        } else {
            SOCKADDR_LAYOUT =  MemoryLayout.ofStruct(
                    MemoryLayouts.JAVA_SHORT.withName("sa_family")
            ).withName("sockaddr");
            carrier = short.class;
        }
        SA_FAMILY_HANDLE = MemoryHandles.asUnsigned(
                SOCKADDR_LAYOUT.varHandle(carrier, groupElement("sa_family")),
                int.class);
    }

    protected final MemorySegment segment;
    protected final MemoryLayout layout;

    protected Sockaddr(MemorySegment segment, MemoryLayout layout) {
        this.segment = segment;
        this.layout = layout;
    }

    public final long rawLongAddress() {
        return segment.baseAddress().toRawLongValue();
    }

    public final void free() {
        segment.close();
    }

    public final int family() {
        int family = (int) SA_FAMILY_HANDLE.get(segment.baseAddress());
        assert family == AF_INET || family == AF_INET6;
        return family;
    }

    public int mismatch(Sockaddr sockaddr){
        return (int) segment.mismatch(sockaddr.segment);
    }

    private final static VarHandle BYTE_HANDLE = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
            .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());

    @Override
    public int hashCode() {
        int h = 0;
        MemoryAddress addr = segment.baseAddress();
        for (int i = 0; i < segment.byteSize(); i++) {
            h = 31 * h + (byte) BYTE_HANDLE.get(addr, (long) i);
        }
        return h;
    }
}
