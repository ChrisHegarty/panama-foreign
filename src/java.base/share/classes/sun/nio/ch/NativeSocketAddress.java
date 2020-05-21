/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

import jdk.internal.access.JavaNetInetAddressAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.layout.Sockaddr;
import jdk.internal.layout.Sockaddr_in;
import jdk.internal.layout.Sockaddr_in6;
import static jdk.internal.layout.Sockaddr.AF_INET;
import static jdk.internal.layout.Sockaddr.AF_INET6;


/**
 * A native socket address ...
 *
 * This class is not thread safe.
 */
public final class NativeSocketAddress {

    private static final JavaNetInetAddressAccess JNINA = SharedSecrets.getJavaNetInetAddressAccess();

    private final Sockaddr sockaddr;
    private final long address;

    long address() {
        return address;
    }

    NativeSocketAddress() {
        sockaddr = Sockaddr_in6.allocate();
        address = sockaddr.rawLongAddress();
    }

    /**
     * Allocate an array of native socket addresses.
     */
    public static NativeSocketAddress[] allocate(int count) {
        NativeSocketAddress[] array = new NativeSocketAddress[count];
        for (int i = 0; i < count; i++) {
            try {
                array[i] = new NativeSocketAddress();
            } catch (OutOfMemoryError e) {
                freeAll(array);
                throw e;
            }
        }
        return array;
    }

    /**
     * Free all non-null native socket addresses in the given array.
     */
    static void freeAll(NativeSocketAddress[] array) {
        for (int i = 0; i < array.length; i++) {
            NativeSocketAddress sa = array[i];
            if (sa != null) {
                sa.sockaddr.free();
            }
        }
    }

    /**
     * Encodes the given InetSocketAddress into this socket address.
     * @param protocolFamily protocol family
     * @param isa the InetSocketAddress to encode
     * @return the size of the socket address (sizeof sockaddr or sockaddr6)
     * @throws UnsupportedAddressTypeException if the address type is not supported
     */
    int encode(ProtocolFamily protocolFamily, InetSocketAddress isa) {
        if (protocolFamily == StandardProtocolFamily.INET) {
            InetAddress ia = isa.getAddress();
            if (!(ia instanceof Inet4Address))
                throw new UnsupportedAddressTypeException();
            Sockaddr_in sockaddr_in = Sockaddr_in.from(sockaddr);
            sockaddr_in.setFamily();
            sockaddr_in.setPort(isa.getPort());
            sockaddr_in.setAddr(JNINA.addressValue((Inet4Address) ia));
            System.out.println("HEGO: toString: " + toString());

            return Sockaddr_in.SIZE;
        } else {
            Sockaddr_in6 sockaddr_in6 = Sockaddr_in6.from(sockaddr);
            sockaddr_in6.setFamily();
            sockaddr_in6.setPort(isa.getPort());
            sockaddr_in6.setAddr(ipv6AddressBytes(isa.getAddress()));
            sockaddr_in6.setScopeId(scopeidFromAddress(isa.getAddress()));
            sockaddr_in6.setFlowInfo(0);
            return Sockaddr_in6.SIZE;
        }
    }

    /**
     * Return an InetSocketAddress to represent the socket address in this buffer.
     * @throws SocketException if the socket address is not AF_INET or AF_INET6
     */
    InetSocketAddress decode() {
        try {
            int family = sockaddr.family();
            InetAddress addr;
            int port;
            if (family == AF_INET) {
                Sockaddr_in sockaddr_in = Sockaddr_in.from(sockaddr);
                port = sockaddr_in.port();
                addr = InetAddress.getByAddress(sockaddr_in.addr());
            } else {
                Sockaddr_in6 sockaddr_in6 = Sockaddr_in6.from(sockaddr);
                port = sockaddr_in6.port();
                int scope = sockaddr_in6.getScopeId();
                if (scope ==0 )
                    addr = InetAddress.getByAddress(sockaddr_in6.addr());
                else
                    addr = Inet6Address.getByAddress(null, sockaddr_in6.addr(), scope);
            }
            return new InetSocketAddress(addr, port);
        } catch (UnknownHostException e) {
            throw new InternalError("should not reach here", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeSocketAddress))
            return false;
        NativeSocketAddress that = (NativeSocketAddress) other;
        return this.sockaddr.mismatch(that.sockaddr) == -1;
    }

    @Override
    public int hashCode() {
        return sockaddr.hashCode();
    }

    @Override
    public String toString() {
        int family = sockaddr.family();

        if (family == AF_INET || family == AF_INET6) {
            return ((family == AF_INET) ? "AF_INET" : "AF_INET6")
                    + ", address=" + decode();
        } else {
            return "<unknown>";
        }
    }

    private static int scopeidFromAddress(InetAddress ia) {
        if (ia instanceof Inet4Address)
            return 0;
        return ((Inet6Address) ia).getScopeId();
    }

    private static byte[] ipv6AddressBytes(InetAddress ia) {
        if (ia instanceof Inet4Address) {
            byte[] ba = new byte[16];
            ba[10] = (byte) 0xFF;
            ba[11] = (byte) 0xFF;
            int ipAddress = JNINA.addressValue((Inet4Address) ia);
            // network order
            ba[12] = (byte) ((ipAddress >>> 24) & 0xFF);
            ba[13] = (byte) ((ipAddress >>> 16) & 0xFF);
            ba[14] = (byte) ((ipAddress >>> 8)  & 0xFF);
            ba[15] = (byte) (ipAddress          & 0xFF);
            return ba;
        }
        return JNINA.addressBytes((Inet6Address) ia);
    }

    // --

    // TODO:: move these native methods.
    public static native int AFINET();
    public static native int AFINET6();
    private static native int sizeofFamily();
    private static native int offsetFamily();
    static {
        IOUtil.load();
    }
}
