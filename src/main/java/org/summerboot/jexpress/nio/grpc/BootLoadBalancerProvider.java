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
import io.grpc.NameResolver.Listener2;
import io.grpc.NameResolver.ResolutionResult;
import io.grpc.NameResolverProvider;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootLoadBalancerProvider extends NameResolverProvider {

    protected final List<EquivalentAddressGroup> servers;
    protected final String scheme;
    protected String authority;

    public BootLoadBalancerProvider(String scheme, SocketAddress... addresses) {
        this.scheme = scheme;
        this.authority = Arrays.stream(addresses)
                .map(SocketAddress::toString)
                .collect(Collectors.joining(", "));
        this.servers = Arrays.stream(addresses)
                .map(EquivalentAddressGroup::new)
                .collect(Collectors.toList());
    }

    public BootLoadBalancerProvider(String scheme, Map<String, Integer> addressesMap) {
        this.scheme = scheme;
        InetSocketAddress[] addresses = addressesMap.entrySet()
                .stream()
                .map(entry -> new InetSocketAddress(entry.getKey(), entry.getValue()))
                .toArray(InetSocketAddress[]::new);
        this.authority = Arrays.stream(addresses)
                .map(SocketAddress::toString)
                .collect(Collectors.joining(", "));
        this.servers = Arrays.stream(addresses)
                .map(EquivalentAddressGroup::new)
                .collect(Collectors.toList());
    }

    @Override
    public NameResolver newNameResolver(URI notUsedTargetUri, NameResolver.Args args) {
        return new NameResolver() {
            @Override
            public String getServiceAuthority() {
                return authority;//notUsedTargetUri.toString();
            }

            @Override
            public void start(Listener2 listener) {
                listener.onResult(ResolutionResult.newBuilder().setAddresses(servers).setAttributes(Attributes.EMPTY).build());
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
