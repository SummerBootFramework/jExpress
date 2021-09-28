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
package org.summerframework.boot.instrumentation;

import org.summerframework.nio.server.domain.Error;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 * @param <T> parameter
 */
public interface HealthInspector<T extends Object> {

    AtomicLong healthInspectorCounter = new AtomicLong(0);

    List<Error> ping(T... param);
}
