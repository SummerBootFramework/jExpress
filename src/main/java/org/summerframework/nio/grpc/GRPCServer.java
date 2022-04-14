/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.nio.grpc;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class GRPCServer {

    protected static final Logger log = LogManager.getLogger(GRPCServer.class.getName());

    public static ServerCredentials initTLS(KeyManagerFactory kmf, TrustManagerFactory tmf) {
        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder().keyManager(kmf.getKeyManagers());
        if (tmf != null) {
            tlsBuilder.trustManager(tmf.getTrustManagers());
            tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);
        }
        return tlsBuilder.build();
    }

    protected Server server = null;
    protected final String bindingAddr;
    protected final int port;
    protected final ServerCredentials serverCredentials;
    protected final ServerBuilder serverBuilder;

    public GRPCServer(String bindingAddr, int port, KeyManagerFactory kmf, TrustManagerFactory tmf) {
        this(bindingAddr, port, initTLS(kmf, tmf));
    }

    public GRPCServer(String bindingAddr, int port, ServerCredentials serverCredentials) {
        this.bindingAddr = bindingAddr;
        this.port = port;
        this.serverCredentials = serverCredentials;
        if (serverCredentials == null) {
            serverBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(bindingAddr, port));
        } else {
            serverBuilder = Grpc.newServerBuilderForPort(port, serverCredentials);
        }
        //AbstractImplBase implBase = 
        //serverBuilder.addService(implBase);
    }

    public ServerBuilder serverBuilder() {
        return serverBuilder;
    }

    public void start(boolean isBlock) throws IOException, InterruptedException {
        if (server != null) {
            stop();
        }
        server = serverBuilder.build().start();
        log.info("*** GRPCServer is listening on " + bindingAddr + ":" + port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    stop();
                }, "GRPCServer.shutdown and stop listening on " + bindingAddr + ":" + port));
        if (isBlock) {
            server.awaitTermination();
        }
    }

    public void stop() {
        if (server == null) {
            return;
        }
        try {
            server.shutdown();
            log.warn("*** GRPCServer shutdown " + bindingAddr + ":" + port);
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("GRPCServer shutdown timeout " + bindingAddr + ":" + port);
        } finally {
            server = null;
        }
    }
}
