/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.hadoop.shuffle;

import org.gridgain.grid.*;
import org.gridgain.grid.hadoop.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.io.*;
import org.gridgain.grid.util.offheap.unsafe.*;
import org.gridgain.grid.util.worker.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.hadoop.GridHadoopJobProperty.*;
import static org.gridgain.grid.util.offheap.unsafe.GridUnsafeMemory.*;

/**
 * Shuffle job.
 */
public class GridHadoopShuffleJob<T> implements AutoCloseable {
    /** */
    private final GridHadoopJob job;

    /** */
    private final GridUnsafeMemory mem;

    /** */
    private final GridHadoopPartitioner partitioner;

    /** */
    private GridHadoopMultimap combinerMap;

    /** Reducers addresses. */
    private T[] reduceAddrs;

    /** Local reducers address. */
    private T locReduceAddr;

    /** */
    private GridHadoopShuffleMessage[] msgs;

    /** */
    private final AtomicReferenceArray<GridHadoopMultimap> maps;

    /** */
    private GridBiClosure<T, GridHadoopShuffleMessage, GridFuture<?>> io;

    /** */
    private Collection<GridFuture<?>> ioFuts = new ConcurrentLinkedQueue<>();

    /** */
    private GridWorker sender;

    /** */
    private final GridLogger log;

    /**
     * @param locReduceAddr Local reducer address.
     * @param log Logger.
     * @param job Job.
     * @param mem Memory.
     * @param reducers Number of reducers for job.
     * @param hasLocMappers {@code True} if
     */
    public GridHadoopShuffleJob(T locReduceAddr, GridLogger log, GridHadoopJob job, GridUnsafeMemory mem,
        int reducers, boolean hasLocMappers) throws GridException {
        this.locReduceAddr = locReduceAddr;
        this.job = job;
        this.mem = mem;
        this.log = log;

        partitioner = job.partitioner();

        maps = new AtomicReferenceArray<>(reducers);
        msgs = new GridHadoopShuffleMessage[reducers];

        if (job.hasCombiner() && hasLocMappers) // We have combiner and local mappers.
            combinerMap = new GridHadoopMultimap(job, mem, get(job, COMBINER_HASHMAP_SIZE, 8 * 1024));
    }

    /**
     * @param reduceAddresses Addresses of reducers.
     * @return {@code True} if addresses were initialized by this call.
     */
    public boolean initializeReduceAddresses(T[] reduceAddresses) {
        if (reduceAddrs == null) {
            reduceAddrs = reduceAddresses;

            return true;
        }

        return false;
    }

    /**
     * @return {@code True} if reducers addresses were initialized.
     */
    public boolean reducersInitialized() {
        return reduceAddrs != null;
    }

    /**
     * @param gridName Grid name.
     * @param io IO Closure for sending messages.
     */
    public void startSending(String gridName, GridBiClosure<T, GridHadoopShuffleMessage, GridFuture<?>> io) {
        assert sender == null;
        assert io != null;

        this.io = io;

        sender = new GridWorker(gridName, "hadoop-shuffle-" + job.id(), log) {
            @Override protected void body() throws InterruptedException {
                while (!isCancelled()) {
                    Thread.sleep(10);

                    try {
                        collectUpdatesAndSend(false);
                    }
                    catch (GridException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        };

        new GridThread(sender).start();
    }

    /**
     * @param maps Maps.
     * @param idx Index.
     * @return Map.
     */
    private GridHadoopMultimap getOrCreateMap(AtomicReferenceArray<GridHadoopMultimap> maps, int idx) {
        GridHadoopMultimap map = maps.get(idx);

        if (map == null) { // Create new map.
            map = new GridHadoopMultimap(job, mem, get(job, PARTITION_HASHMAP_SIZE, 8 * 1024));

            if (!maps.compareAndSet(idx, null, map)) {
                map.close();

                return maps.get(idx);
            }
        }

        return map;
    }

    /**
     * @param msg Message.
     * @throws GridException Exception.
     */
    public void onShuffleMessage(GridHadoopShuffleMessage msg) throws GridException {
        assert msg.buffer() != null;
        assert msg.offset() > 0;

        GridHadoopMultimap map = getOrCreateMap(maps, msg.reducer());

        // Add data from message to the map.
        try (GridHadoopMultimap.Adder adder = map.startAdding()) {
            final GridUnsafeDataInput dataInput = new GridUnsafeDataInput();
            final UnsafeValue val = new UnsafeValue(msg.buffer());

            msg.visit(new GridHadoopShuffleMessage.Visitor() {
                /** */
                private GridHadoopMultimap.Key key;

                @Override public void onKey(byte[] buf, int off, int len) throws GridException {
                    dataInput.bytes(buf, off, off + len);

                    key = adder.addKey(dataInput, key);
                }

                @Override public void onValue(byte[] buf, int off, int len) {
                    val.off = off;
                    val.size = len;

                    key.add(val);
                }
            });
        }
    }

    /**
     * Unsafe value.
     */
    private static class UnsafeValue implements GridHadoopMultimap.Value {
        /** */
        private final byte[] buf;

        /** */
        private int off;

        /** */
        private int size;

        /**
         * @param buf Buffer.
         */
        private UnsafeValue(byte[] buf) {
            assert buf != null;

            this.buf = buf;
        }

        /** */
        @Override public int size() {
            return size;
        }

        /** */
        @Override public void copyTo(long ptr) {
            UNSAFE.copyMemory(buf, BYTE_ARR_OFF + off, null, ptr, size);
        }
    }

    /**
     * Sends map updates to remote reducers.
     */
    private void collectUpdatesAndSend(boolean flush) throws GridException {
        for (int i = 0; i < maps.length(); i++) {
            GridHadoopMultimap map = maps.get(i);

            if (map == null || locReduceAddr.equals(reduceAddrs[i]))
                continue; // Skip empty map and local node.

            if (msgs[i] == null)
                msgs[i] = new GridHadoopShuffleMessage(job.id(), i, 4 * 1024);

            final int idx = i;

            map.visit(false, new GridHadoopMultimap.Visitor() {
                /** */
                private long keyPtr;

                /** */
                private int keySize;

                /** */
                private boolean keyAdded;

                /** {@inheritDoc} */
                @Override public void onKey(long keyPtr, int keySize) {
                    this.keyPtr = keyPtr;
                    this.keySize = keySize;

                    keyAdded = false;
                }

                private boolean tryAdd(long valPtr, int valSize) {
                    GridHadoopShuffleMessage msg = msgs[idx];

                    if (!keyAdded) { // Add key and value.
                        int size = keySize + valSize;

                        if (!msg.available(size, false))
                            return false;

                        msg.addKey(keyPtr, keySize);
                        msg.addValue(valPtr, valSize);

                        keyAdded = true;

                        return true;
                    }

                    if (!msg.available(valSize, true))
                        return false;

                    msg.addValue(valPtr, valSize);

                    return true;
                }

                /** {@inheritDoc} */
                @Override public void onValue(long valPtr, int valSize) {
                    if (tryAdd(valPtr, valSize))
                        return;

                    send(idx, keySize + valSize);

                    keyAdded = false;

                    if (!tryAdd(valPtr, valSize))
                        throw new IllegalStateException();
                }
            });

            if (flush && msgs[i].offset() != 0)
                send(i, 0);
        }
    }

    /**
     * @param idx Index of message.
     * @param newBufMinSize Min new buffer size.
     */
    private void send(int idx, int newBufMinSize) {
        GridFuture<?> fut = io.apply(reduceAddrs[idx], msgs[idx]);

        fut.listenAsync(new GridInClosure<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> f) {
                try {
                    f.get();
                }
                catch (GridException e) {
                    log.error("Failed to send message.", e);
                }
            }
        });

        ioFuts.add(fut);

        msgs[idx] = newBufMinSize == 0 ? null : new GridHadoopShuffleMessage(job.id(), idx,
            Math.max(4 * 1024, newBufMinSize));
    }

    /** {@inheritDoc} */
    @Override public void close() throws GridException {
        if (sender != null) {
            sender.cancel();

            try {
                sender.join();
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException(e);
            }
        }

        close(maps);
    }

    /**
     * @param maps Maps.
     */
    private void close(AtomicReferenceArray<GridHadoopMultimap> maps) {
        for (int i = 0; i < maps.length(); i++) {
            GridHadoopMultimap map = maps.get(i);

            if (map != null)
                map.close();
        }
    }

    /**
     * @return Future.
     */
    @SuppressWarnings("unchecked")
    public GridFuture<?> flush() throws GridException {
        sender.cancel();

        try {
            sender.join();
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }

        collectUpdatesAndSend(true); // With flush.

        GridCompoundFuture fut = new GridCompoundFuture<>();

        for (GridFuture<?> f : ioFuts)
            fut.add(f);

        fut.markInitialized();

        return fut;
    }

    /**
     * @param taskInfo Task info.
     * @return Output.
     * @throws GridException If failed.
     */
    public GridHadoopTaskOutput output(GridHadoopTaskInfo taskInfo) throws GridException {
        switch (taskInfo.type()) {
            case MAP:
                if (combinerMap != null)
                    return new MapOutput(combinerMap.startAdding());

            case COMBINE:
                return new PartitionedOutput();

            default:
                throw new IllegalStateException("Illegal type: " + taskInfo.type());
        }
    }

    /**
     * @param taskInfo Task info.
     * @return Input.
     * @throws GridException If failed.
     */
    public GridHadoopTaskInput input(GridHadoopTaskInfo taskInfo) throws GridException {
        switch (taskInfo.type()) {
            case COMBINE:
                return combinerMap.input();

            case REDUCE:
                int reducer = taskInfo.taskNumber();

                GridHadoopMultimap m = maps.get(reducer);

                if (m != null)
                    return m.input();

                return new GridHadoopTaskInput() { // Empty input.
                    @Override public boolean next() {
                        return false;
                    }

                    @Override public Object key() {
                        throw new IllegalStateException();
                    }

                    @Override public Iterator<?> values() {
                        throw new IllegalStateException();
                    }

                    @Override public void close() throws Exception {
                        // No-op.
                    }
                };

            default:
                throw new IllegalStateException("Illegal type: " + taskInfo.type());
        }
    }

    /**
     * Map output.
     */
    private class MapOutput implements GridHadoopTaskOutput {
        /** */
        private final GridHadoopMultimap.Adder adder;

        /**
         * @param adder Map adder.
         */
        private MapOutput(GridHadoopMultimap.Adder adder) {
            this.adder = adder;
        }

        /** {@inheritDoc} */
        @Override public void write(Object key, Object val) throws GridException {
            adder.add(key, val);
        }

        /** {@inheritDoc} */
        @Override public void close() throws GridException {
            adder.close();
        }
    }

    /**
     * Partitioned output.
     */
    private class PartitionedOutput implements GridHadoopTaskOutput {
        /** */
        private GridHadoopMultimap.Adder[] adders = new GridHadoopMultimap.Adder[maps.length()];

        /** {@inheritDoc} */
        @Override public void write(Object key, Object val) throws GridException {
            int part = partitioner.partition(key, val, adders.length);

            if (part < 0 || part >= adders.length)
                throw new IllegalStateException("Invalid partition: " + part);

            GridHadoopMultimap.Adder adder = adders[part];

            if (adder == null)
                adders[part] = adder = getOrCreateMap(maps, part).startAdding();

            adder.add(key, val);
        }

        /** {@inheritDoc} */
        @Override public void close() throws GridException {
            for (GridHadoopMultimap.Adder adder : adders) {
                if (adder != null)
                    adder.close();
            }
        }
    }
}
