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
import jdk.incubator.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public final class Sockaddr_in extends Sockaddr{

    private static final MemoryLayout LAYOUT = SockaddrLayout.sockaddr_in;

    public static final int SIZE = (int) LAYOUT.byteSize();

    private Sockaddr_in(MemorySegment segment) {
        super(segment, LAYOUT);
    }

    public static Sockaddr_in from(Sockaddr sockaddr) {
        if (sockaddr instanceof Sockaddr_in)
            return (Sockaddr_in) sockaddr;
        return new Sockaddr_in(sockaddr.segment);
    }

    // -- family

    public void setFamily() {
        FAMILY.set(segment.baseAddress(), AF_INET);
    }

    // -- port

    private static final VarHandle SIN_PORT_HANDLE = MemoryHandles.asUnsigned(
            SockaddrLayout.SIN_PORT_HANDLE,
            int.class);

    public int port() {
        assert family() == AF_INET;
        return (int) SIN_PORT_HANDLE.get(segment.baseAddress());
    }

    public void setPort(int port) {
        assert family() == AF_INET;
        SIN_PORT_HANDLE.set(segment.baseAddress(), port);
    }

    // -- addr

    public byte[] addr() {
        assert family() == AF_INET;
        return intToByteArray((int) SockaddrLayout.SIN_ADDR_HANDLE.get(segment.baseAddress()));
    }

    public void setAddr(int addr) {
        assert family() == AF_INET;
        SockaddrLayout.SIN_ADDR_HANDLE.set(segment.baseAddress(), addr);
    }

    // --

    private static final byte[] intToByteArray(int value) {
        return new byte[] { (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte)  value};
    }
//
//    private static final byte[] byteArrayToInt(byte[] ba) {
//        assert ba.length == 4;
//        return new byte[] { (byte) (value >>> 24),
//                (byte) (value >>> 16),
//                (byte) (value >>> 8),
//                (byte)  value};
//    }
}
