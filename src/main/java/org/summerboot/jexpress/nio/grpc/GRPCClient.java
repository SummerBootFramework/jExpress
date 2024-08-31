
/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License and you have no
 * policy prohibiting employee contributions back to this file (unless the contributor to this
 * file is your current or retired employee). You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.summerboot.jexpress.nio.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @param <T>
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class GRPCClient<T extends GRPCClient<T>> {

    protected NettyChannelBuilder channelBuilder;
    protected ManagedChannel channel;
    protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    protected final Lock readLock = rwLock.readLock();
    protected Thread shutdownHook;

    public GRPCClient(GRPCClientConfig cfg) {
        this.channelBuilder = cfg.getChannelBuilder();
        cfg.addConfigUpdateListener(this);
    }

    /**
     * @param channelBuilder
     */
    public GRPCClient(NettyChannelBuilder channelBuilder) {
        this.channelBuilder = channelBuilder;
    }

    /**
     * callback when config file updated if GRPCClientConfig.addConfigUpdateListener(this);
     *
     * @param channelBuilder
     */
    protected void updateChannelBuilder(NettyChannelBuilder channelBuilder) {
        rwLock.writeLock().lock();
        this.channelBuilder = channelBuilder;
        rwLock.writeLock().unlock();
        onChannelBuilderUpdated();
    }

    /**
     * Disconnect the current connection and build a new connection within a write lock
     *
     * @return
     */
    public T connect() {
        rwLock.writeLock().lock();
        try {
            disconnect();
            channel = channelBuilder.build();
            String info = channel.authority();
            shutdownHook = new Thread(() -> {
                try {
                    channel.shutdownNow();
                } catch (Throwable ex) {
                }
            }, "GRPCClient.shutdown and disconnect from " + info);
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            onConnected(channel);
        } finally {
            rwLock.writeLock().unlock();
        }
        return (T) this;
    }

    /**
     * normally just call connect() to establish a new connection with the updated settings; or do nothing to keep using current connection.
     */
    protected abstract void onChannelBuilderUpdated();

    /**
     * @param channel
     */
    protected abstract void onConnected(ManagedChannel channel);

    /**
     * Set a read lock for business method to prevent being called while connect/disconnect
     */
    protected void lock() {
        readLock.lock();
    }

    /**
     * Try a read lock for business method to prevent being called while connect/disconnect
     *
     * @return
     */
    protected boolean tryLock() {
        return readLock.tryLock();
    }

    /**
     * Try a read lock in a given time period for business method to prevent being called while connect/disconnect
     *
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    protected boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return readLock.tryLock(time, unit);
    }

    /**
     * Release the read lock for business method to prevent being called while connect/disconnect
     */
    protected void unlock() {
        readLock.unlock();
    }

    /**
     * Get the read lock
     *
     * @return
     */
    protected Lock getLock() {
        return readLock;
    }

    /**
     * Disconnect the current connection
     */
    public void disconnect() {
//        ManagedChannel c = (ManagedChannel) blockingStub.getChannel();
        if (channel != null) {
            try {
                channel.shutdownNow();
            } catch (Throwable ex) {
            } finally {
                channel = null;
            }
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (Throwable ex) {
            } finally {
                shutdownHook = null;
            }
        }
    }
}
