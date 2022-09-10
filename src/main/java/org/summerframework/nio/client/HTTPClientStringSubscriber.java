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
package org.summerframework.nio.client;

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

/**
 *
 * @author daniel, Changski Tie Zheng Zhang, Du Xiao
 */
public class HTTPClientStringSubscriber implements Flow.Subscriber<ByteBuffer> {

    final BodySubscriber<String> wrapped;

    HTTPClientStringSubscriber(BodySubscriber<String> wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        wrapped.onSubscribe(subscription);
    }

    @Override
    public void onNext(ByteBuffer item) {
        wrapped.onNext(List.of(item));
    }

    @Override
    public void onError(Throwable throwable) {
        wrapped.onError(throwable);
    }

    @Override
    public void onComplete() {
        wrapped.onComplete();
    }
}

/* usage
        // one of the subscribers is likely to fail if concurrently subscribed to the body publisher when executing this code,
        String reqbody = null;
        Optional<HttpRequest.BodyPublisher> pub = req.bodyPublisher();
        if (pub.isPresent()) {
            reqbody = pub.map(p -> {
                var bodySubscriber = BodySubscribers.ofString(StandardCharsets.UTF_8);
                var flowSubscriber = new HTTPClientStringSubscriber(bodySubscriber);
                p.subscribe(flowSubscriber);
                return bodySubscriber.getBody().toCompletableFuture().join();
            }).get();
        }
        System.out.println(reqbody);
 */
