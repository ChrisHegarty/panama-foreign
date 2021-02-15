/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @modules java.base/sun.nio.ch
 *          jdk.incubator.foreign/jdk.internal.foreign
 * @run testng/othervm -Dforeign.restricted=permit TestIOWithByteBuffers
 */


import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.testng.annotations.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.*;

public class TestIOWithByteBuffers {

    static Path tempPath;

    static {
        try {
            File file = File.createTempFile("buffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
            Files.write(file.toPath(), new byte[256], StandardOpenOption.WRITE);

        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final Class<IllegalStateException> ISE = IllegalStateException.class;
    static final Class<IOException> IOE = IOException.class;
    static final Class<ExecutionException> EE = ExecutionException.class;

    @Test
    public void testIOOnSharedSegmentBuffer() throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ) ;
             ResourceScope scope = ResourceScope.ofShared()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, scope);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            channel.read(bb);  // TODO:add scatter/gather
            channel.write(bb);
        }
    }

    @DataProvider
    public Object[][] resourceScopeFactories() {
        List<Supplier<ResourceScope>> l = List.of(
                () -> ResourceScope.ofShared(),
                () -> ResourceScope.ofConfined());
        return l.stream().map(e -> new Object[] { e }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "resourceScopeFactories")
    public void testNetworkIOOnClosedSegmentBuffer(Supplier<ResourceScope> resourceScopeSupplier)
        throws Exception
    {
        try (var ssc = ServerSocketChannel.open();
             var sc1 = SocketChannel.open();
             var assc = AsynchronousServerSocketChannel.open();
             var asc1 = AsynchronousSocketChannel.open();
             var sc2 = connectChannels(ssc, sc1);
             var asc2 = connectAsyncChannels(assc, asc1)) {
            ResourceScope scope = resourceScopeSupplier.get();
            MemorySegment segment = MemorySegment.allocateNative(10, 1, scope);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            segment.scope().close();

            // synchronous
            assertThrows(ISE, () -> sc1.write(bb));
            assertThrows(ISE, () -> sc1.read(bb));
            assertThrows(ISE, () -> sc1.write(new ByteBuffer[] { bb }));
            assertThrows(ISE, () -> sc1.read( new ByteBuffer[] { bb }));
            assertThrows(ISE, () -> sc1.write(new ByteBuffer[] { bb }, 0, 1));
            assertThrows(ISE, () -> sc1.read( new ByteBuffer[] { bb }, 0 ,1));
            var heapBB = ByteBuffer.allocate(10);
            assertThrows(ISE, () -> sc1.write(new ByteBuffer[] { heapBB, bb }));
            assertThrows(ISE, () -> sc1.read( new ByteBuffer[] { heapBB, bb }));
            assertThrows(ISE, () -> sc1.write(new ByteBuffer[] { bb, heapBB }));
            assertThrows(ISE, () -> sc1.read( new ByteBuffer[] { bb, heapBB }));
            assertThrows(ISE, () -> sc1.write(new ByteBuffer[] { bb, heapBB }, 0 ,2));
            assertThrows(ISE, () -> sc1.read( new ByteBuffer[] { bb, heapBB }, 0, 2));

            // asynchronous
            {   // read variants
                var t1 = expectThrows(EE, () -> asc1.read(bb).get());
                var t2 = expectCause(IOE, t1);               // <<<<<< HERE IOException
                var t3 = expectCause(ISE, t2);
            }
            {
                LogCompletionHandler<Integer> handler = new LogCompletionHandler<>();
                asc1.read(bb, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed")); // .MemoryScope$SharedScope.lock - Already closed
            }
            {
                LogCompletionHandler<Integer> handler = new LogCompletionHandler<>();
                asc1.read(bb, 30, SECONDS, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed"));
            }
            {
                LogCompletionHandler<Long> handler = new LogCompletionHandler<>();
                asc1.read(new ByteBuffer[]{ bb }, 0, 1, 30, SECONDS, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed"));
            }
            {   // write variants
                var t1 = expectThrows(EE, () -> asc1.write(bb).get());
                var t2 = expectCause(IOE, t1);               // <<<<<< HERE IOException
                var t3 = expectCause(ISE, t2);
            }
            {
                LogCompletionHandler<Integer> handler = new LogCompletionHandler<>();
                asc1.write(bb, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed"));
            }
            {
                LogCompletionHandler<Integer> handler = new LogCompletionHandler<>();
                asc1.write(bb, 30, SECONDS, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed"));
            }
            {
                LogCompletionHandler<Long> handler = new LogCompletionHandler<>();
                asc1.write(new ByteBuffer[]{ bb }, 0, 1, 30, SECONDS, null, handler);
                handler.latch.await();
                assertEquals(handler.result, null);
                assertThrowable(ISE, handler.throwable);
                assertTrue(handler.throwable.getMessage().contains("Already closed"));
            }
        }
    }

    @Test
    public void testAsyncIOOnSharedSegmentBuffer() throws Exception {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (var assc = AsynchronousServerSocketChannel.open();
             var asc1 = AsynchronousSocketChannel.open();
             ResourceScope scope = ResourceScope.ofShared()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, scope);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();

            var asc2 = connectAsyncChannels(assc, asc1);

            int i = asc1.write(bb).get();
            System.out.println("HEGO: wrote:" + i);
            i = asc2.read(bb).get();
            System.out.println("HEGO: read :" + i);

            //channel.read(bb);  // TODO:add scatter/gather
            //channel.write(bb);
        }
    }

    @Test  // tests uncompleted IO Op locks the buffer's resource scope
    public void testAsyncReadLocksOnSharedSegmentBuffer() throws Exception {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (var assc = AsynchronousServerSocketChannel.open();
             var asc1 = AsynchronousSocketChannel.open();
             ResourceScope scope = ResourceScope.ofShared()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, scope);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();

            // connect the async channels
            var asc2 = connectAsyncChannels(assc, asc1);

            // future returning read variant
            // initiate a read that will not complete
            Future<Integer> future = asc2.read(bb);
            assertFalse(future.isDone());
            assertTrue(scope.isAlive());
            IllegalStateException ise = expectThrows(ISE, () -> scope.close());
            assertEquals(ise.getMessage(), "Cannot close a scope which has active forks");
            assertTrue(scope.isAlive());

            // write to allow the blocking read complete, which will in turn
            // unlock the scope and allow it to be closed.
            asc1.write(ByteBuffer.wrap(new byte[] { 0x01 })).get();
            future.get();
            assertTrue(future.isDone());
            assertTrue(scope.isAlive());
        }
    }

    @Test  // tests uncompleted IO Op locks the buffer's resource scope
    public void testAsyncReadLocksOnSharedSegmentBuffer2() throws Exception {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (var assc = AsynchronousServerSocketChannel.open();
             var asc1 = AsynchronousSocketChannel.open();
             ResourceScope scope = ResourceScope.ofShared()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, scope);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();

            // connect the async channels
            var asc2 = connectAsyncChannels(assc, asc1);

            // completion handler read variant
            // initiate a read that will not complete
            LogCompletionHandler<Integer> handler = new LogCompletionHandler<>();
            asc2.read(bb, 30, SECONDS, null, handler);
            assertFalse(handler.isDone());
            IllegalStateException ise = expectThrows(ISE, () -> scope.close());
            assertEquals(ise.getMessage(), "Cannot close a scope which has active forks");
            assertTrue(scope.isAlive());

            // write to allow the blocking read complete, which will in turn
            // unlock the scope and allow it to be closed.
            asc1.write(ByteBuffer.wrap(new byte[] { 0x01 })).get();
            handler.latch.await();
            assertTrue(handler.isDone());
            assertTrue(scope.isAlive());
        }
    }

    @Test  // tests uncompleted IO Op locks the buffer's resource scope
    public void testAsyncWriteLocksOnSharedSegmentBuffer() throws Exception {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (var assc = AsynchronousServerSocketChannel.open();
             var asc1 = AsynchronousSocketChannel.open();
             ResourceScope scope = ResourceScope.ofShared()) {
            MemorySegment segment = MemorySegment.allocateNative(8096, 1, scope);
            for (int i = 0; i < 8096; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();

            // connect the async channels
            var asc2 = connectAsyncChannels(assc, asc1);

            // set to true to signal that no more buffers should be written
            final AtomicBoolean continueWriting = new AtomicBoolean(true);
            // number of bytes written
            final AtomicLong bytesWritten = new AtomicLong(0);

            // write until socket buffer is full so as to create the conditions
            // for when a write does not complete immediately
            ByteBuffer[] srcs = new ByteBuffer[] { bb };
            asc1.write(srcs, 0, srcs.length, 0L, TimeUnit.SECONDS, null,
                    new CompletionHandler<Long,Void>() {
                        public void completed(Long result, Void att) {
                            long n = result;
                            if (n <= 0)
                                throw new RuntimeException("No bytes written");
                            bytesWritten.addAndGet(n);
                            if (continueWriting.get()) {
                                ByteBuffer[] srcs = genBuffers(8);
                                ch.write(srcs, 0, srcs.length, 0L, TimeUnit.SECONDS,
                                        (Void)null, this);
                            }
                        }
                        public void failed(Throwable exc, Void att) {
                        }
                    });

            // give time for socket buffer to fill up.
            Thread.sleep(5*1000);

            // signal handler to stop further writing
            continueWriting.set(false);
        }
    }

    static class LogCompletionHandler<V> implements CompletionHandler<V,Void> {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile V result;
        volatile Throwable throwable;

        @Override
        public void completed(V result, Void attachment) {
            this.result = result;
            latch.countDown();
        }
        @Override
        public void failed(Throwable exc, Void attachment) {
            this.throwable = exc;
            latch.countDown();
        }
        boolean isDone() {
            return latch.getCount() == 0;
        }
    }

    static void assertThrowable(Class<?> throwableClass, Throwable t) {
        assertTrue(throwableClass.isInstance(t), "Expected:" +  throwableClass + ", got:" + t);
    }

    static <T extends Throwable> T expectCause(Class<T> throwableClass,
                                               Throwable throwable) {
        @SuppressWarnings("unchecked") T cause = (T)throwable.getCause();
        assertTrue(throwableClass.isInstance(cause), "Expected:" +  throwableClass + ", got:" + cause);
        return cause;
    }

    static AsynchronousSocketChannel connectAsyncChannels
        (AsynchronousServerSocketChannel assc, AsynchronousSocketChannel asc)
            throws Exception
    {
        assc.bind(new InetSocketAddress(0));
        asc.connect(assc.getLocalAddress()).get();
        return assc.accept().get();
    }

    static SocketChannel connectChannels(ServerSocketChannel ssc, SocketChannel sc)
        throws IOException
    {
        ssc.bind(new InetSocketAddress(0));
        sc.connect(ssc.getLocalAddress());
        return ssc.accept();
    }
}
