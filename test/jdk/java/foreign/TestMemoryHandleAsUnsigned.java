/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
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

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * @test
 * @run testng TestMemoryHandleAsUnsigned
 */

public class TestMemoryHandleAsUnsigned {

    @DataProvider(name = "byteToLongData")
    public Object[][] byteToLongData() {
        return LongStream.range(0, 512).mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "byteToLongData")
    public void testUnsignedByteFromLong(long longValue) {
        byte byteValue = (byte) (longValue & 0xFFL);

        MemoryLayout layout = MemoryLayouts.JAVA_BYTE;
        VarHandle byteHandle = layout.varHandle(byte.class);
        VarHandle longHandle = MemoryHandles.asUnsigned(byteHandle, long.class);

        try (MemorySegment segment = MemorySegment.allocateNative(layout)) {
            longHandle.set(segment.baseAddress(), longValue);
            long expectedLongValue = Byte.toUnsignedLong(byteValue);
            assertEquals((long) longHandle.get(segment.baseAddress()), expectedLongValue);
            assertEquals((byte) byteHandle.get(segment.baseAddress()), byteValue);
        }
    }

    @DataProvider(name = "byteToIntData")
    public Object[][] byteToIntData() {
        return IntStream.range(0, 512).mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "byteToIntData")
    public void testUnsignedByteFromInt(int intValue) {
        byte byteValue = (byte) (intValue & 0xFF);

        MemoryLayout layout = MemoryLayouts.JAVA_BYTE;
        VarHandle byteHandle = layout.varHandle(byte.class);
        VarHandle intHandle = MemoryHandles.asUnsigned(byteHandle, int.class);

        try (MemorySegment segment = MemorySegment.allocateNative(layout)) {
            intHandle.set(segment.baseAddress(), intValue);
            int expectedIntValue = Byte.toUnsignedInt(byteValue);
            assertEquals((int) intHandle.get(segment.baseAddress()), expectedIntValue);
            assertEquals((byte) byteHandle.get(segment.baseAddress()), byteValue);
        }
    }


}
