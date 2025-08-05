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

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.StatusOr;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootLoadBalancerProvider extends NameResolverProvider {

    protected final List<EquivalentAddressGroup> servers;
    protected final String scheme;
    protected final int priority;
    protected final String defaultAuthorityWhitoutTrustManager;

    public BootLoadBalancerProvider(String scheme, int priority, InetSocketAddress... addresses) {
        this.scheme = scheme;
        this.priority = priority;
        this.defaultAuthorityWhitoutTrustManager = getAuthorityFromAddress(addresses);
        this.servers = Arrays.stream(addresses)
                .map(EquivalentAddressGroup::new)
                .collect(Collectors.toList());
    }

    public BootLoadBalancerProvider(String scheme, int priority, List<? extends InetSocketAddress> addresses) {
        this.scheme = scheme;
        this.priority = priority;
        this.defaultAuthorityWhitoutTrustManager = getAuthorityFromAddress(addresses);
        this.servers = addresses.stream()
                .map(EquivalentAddressGroup::new)
                .collect(Collectors.toList());
    }

    public String getAuthorityFromAddress(InetSocketAddress... addresses) {
//        this.authority = Arrays.stream(addresses)
//                .map(InetSocketAddress::getHostName)
//                .collect(Collectors.joining(", "));
        if (addresses == null || addresses.length < 1) {
            return "unknownhost";
        }
        InetSocketAddress addr = addresses[0];
        return addr.getHostName() + ":" + addr.getPort();
    }

    public String getAuthorityFromAddress(List<? extends InetSocketAddress> addresses) {
//        this.authority = addresses.stream()
//                .map(InetSocketAddress::getHostName)// getHostString
//                .collect(Collectors.joining(", "));
        if (addresses == null || addresses.isEmpty()) {
            return "unknownhost";
        }
        InetSocketAddress addr = addresses.get(0);
        return addr.getHostName() + ":" + addr.getPort();
    }

    @Override
    public NameResolver newNameResolver(URI notUsedTargetUri, NameResolver.Args args) {
        return new NameResolver() {
            @Override
            public String getServiceAuthority() {// called when trust manager is null
                String auth = notUsedTargetUri.getAuthority();
                if (auth == null) {
                    auth = defaultAuthorityWhitoutTrustManager;
                }
                return auth;
            }

            @Override
            public void start(Listener2 listener) {
                listener.onResult(ResolutionResult.newBuilder().setAddressesOrError(StatusOr.fromValue(servers)).setAttributes(Attributes.EMPTY).build());
            }

            @Override
            public void shutdown() {
            }
        };
    }

    @Override
    public String getDefaultScheme() {
        return scheme;
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 0;
    }

}
