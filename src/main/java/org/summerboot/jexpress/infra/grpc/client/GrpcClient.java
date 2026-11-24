
/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.infra.grpc.client;

import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.AbstractStub;
import org.summerboot.jexpress.boot.lifecycle.app.IdleEventMonitor;
import org.summerboot.jexpress.infra.grpc.client.config.GrpcClientConfig;
import org.summerboot.jexpress.infra.grpc.server.interceptor.BearerAuthCredential;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Base gRPC client managing channel lifecycle, thread-safe reconnection,
 * dynamic configuration updates, and idle connection monitoring.
 *
 * @param <T> the concrete subclass type for fluent method chaining
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class GrpcClient<T extends GrpcClient<T>> implements IdleEventMonitor.IdleEventListener, AutoCloseable {

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private Thread shutdownHook;

    protected NettyChannelBuilder channelBuilder;
    protected ManagedChannel channel;

    protected final IdleEventMonitor idleEventMonitor;

    private final ClientInterceptor idleResetInterceptor;
    private boolean interceptorRegistered;

    protected CallCredentials callCredentials;

    /**
     * Constructs a GrpcClient using a {@link GrpcClientConfig} and registers this client
     * as a configuration update listener.
     *
     * @param cfg the client configuration (must not be null)
     */
    protected GrpcClient(GrpcClientConfig cfg) {
        this(
                Objects.requireNonNull(cfg, "GrpcClientConfig must not be null").getChannelBuilder(),
                cfg.getIdleEventMonitorName(),
                cfg.getIdleThresholdSecond()
        );
        // Register listener only after all fields are completely initialized to prevent 'this' escape
        cfg.addConfigUpdateListener(this);
    }

    /**
     * Constructs a GrpcClient with an explicit {@link NettyChannelBuilder} and idle monitoring settings.
     *
     * @param channelBuilder       the NettyChannelBuilder used to create channels (must not be null)
     * @param idleEventMonitorName name identifier for the idle monitor
     * @param idleThresholdSecond  idle timeout threshold in seconds (must be positive)
     */
    protected GrpcClient(NettyChannelBuilder channelBuilder, String idleEventMonitorName, int idleThresholdSecond) {
        this.channelBuilder = Objects.requireNonNull(channelBuilder, "NettyChannelBuilder must not be null");
        if (idleThresholdSecond <= 0) {
            throw new IllegalArgumentException("idleThresholdSecond must be positive");
        }
        this.idleEventMonitor = new IdleEventMonitor(idleEventMonitorName) {
            @Override
            public long getIdleIntervalMillis() {
                return TimeUnit.SECONDS.toMillis(idleThresholdSecond);
            }
        };
        this.idleResetInterceptor = new GrpcClientInterceptor(this.idleEventMonitor);
    }

    /**
     * Callback invoked when the configuration is updated via {@link GrpcClientConfig}.
     *
     * @param newChannelBuilder the updated channel builder (must not be null)
     */
    public void updateChannelBuilder(NettyChannelBuilder newChannelBuilder) {
        Objects.requireNonNull(newChannelBuilder, "NettyChannelBuilder must not be null");
        rwLock.writeLock().lock();
        try {
            if (this.channelBuilder != newChannelBuilder) {
                this.channelBuilder = newChannelBuilder;
                this.interceptorRegistered = false; // Reset flag only if a new builder instance is provided
            }
        } finally {
            rwLock.writeLock().unlock();
        }
        onChannelBuilderUpdated();
    }

    /**
     * Hook triggered after {@link #updateChannelBuilder(NettyChannelBuilder)} completes.
     * By default, immediately reconnects using the updated settings. Override to customize behavior.
     */
    protected void onChannelBuilderUpdated() {
        connect(this.callCredentials);
    }

    /**
     * Disconnects any existing channel and establishes a new connection under a write lock.
     * Rolls back partially initialized resources if connection setup fails.
     *
     * @return this client instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T connect() {
        return connect((CallCredentials) null);
    }

    /**
     * Disconnects any existing channel and establishes a new connection under a write lock.
     * Rolls back partially initialized resources if connection setup fails.
     *
     * @param jwt
     * @return this client instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T connect(String jwt) {
        return connect(jwt == null ? null : new BearerAuthCredential(jwt));
    }

    /**
     * Disconnects any existing channel and establishes a new connection under a write lock.
     * Rolls back partially initialized resources if connection setup fails.
     *
     * @param callCredentials
     * @return this client instance for chaining
     */
    @SuppressWarnings("unchecked")
    public T connect(CallCredentials callCredentials) {
        rwLock.writeLock().lock();
        try {
            disconnect(false);

            if (!interceptorRegistered) {
                channelBuilder.intercept(idleResetInterceptor);
                interceptorRegistered = true;
            }

            final ManagedChannel newChannel = channelBuilder.build();
            this.channel = newChannel;

            String info = newChannel.authority();
            shutdownHook = new Thread(() -> {
                try {
                    newChannel.shutdownNow();
                } catch (Exception ignore) {
                }
            }, "GrpcClient.shutdown and disconnect from " + info);
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            this.callCredentials = callCredentials;
            onConnected(newChannel).withCallCredentials(callCredentials);
            IdleEventMonitor.start(idleEventMonitor, this);
        } catch (Throwable t) {
            // Ensure partial resources (channel, shutdownHook) are cleaned up if onConnected() fails
            disconnect(false);
            throw t;
        } finally {
            rwLock.writeLock().unlock();
        }
        return (T) this;
    }

    /**
     * Resets the idle timer without specifying a caller identifier.
     */
    protected void resetIdleTimer() {
        resetIdleTimer(null);
    }

    /**
     * Resets the idle timer, recording the method or component that accessed the client.
     *
     * @param accessedBy identifier of the caller (e.g., gRPC full method name)
     */
    protected void resetIdleTimer(String accessedBy) {
        idleEventMonitor.resetIdleTimer(accessedBy);
    }

    /**
     * Invoked during {@link #connect(CallCredentials)} once the {@link ManagedChannel} is built.
     * Subclasses should initialize their gRPC stubs here.
     * <p>
     * <b>Warning:</b> Do not dispatch blocking work to external threads that attempt to acquire
     * {@link #lock()} inside this method, as the current thread holds the write lock and will deadlock.
     *
     * @param channel the newly established ManagedChannel
     * @return
     */
    protected abstract AbstractStub onConnected(ManagedChannel channel);

    /**
     * Disconnects the current channel and stops idle monitoring in a thread-safe manner.
     */
    public void disconnect() {
        disconnect(true);
    }

    protected void disconnect(boolean withLock) {
        if (withLock) {
            rwLock.writeLock().lock();
        }
        try {
            try {
                IdleEventMonitor.stop(idleEventMonitor);
            } catch (Exception ignore) {
            }

            if (channel != null) {
                try {
                    channel.shutdownNow();
                } catch (Exception ignore) {
                } finally {
                    channel = null;
                }
            }

            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (Exception ignore) {
                } finally {
                    shutdownHook = null;
                }
            }
        } finally {
            if (withLock) {
                rwLock.writeLock().unlock();
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    /**
     * Acquires the read lock for business RPC calls to prevent concurrent connect/disconnect operations.
     */
    protected void lock() {
        readLock.lock();
    }

    /**
     * Attempts to acquire the read lock immediately without blocking.
     *
     * @return {@code true} if the lock was acquired
     */
    protected boolean tryLock() {
        return readLock.tryLock();
    }

    /**
     * Attempts to acquire the read lock within the given waiting time.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return {@code true} if the lock was acquired
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    protected boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return readLock.tryLock(time, unit);
    }

    /**
     * Releases the read lock acquired for business RPC calls.
     */
    protected void unlock() {
        readLock.unlock();
    }

    /**
     * Returns the read lock used to guard RPC executions against lifecycle transitions.
     *
     * @return the read lock
     */
    protected Lock getLock() {
        return readLock;
    }
}
