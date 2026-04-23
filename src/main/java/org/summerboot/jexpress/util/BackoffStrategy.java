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
package org.summerboot.jexpress.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Exponential Backoff Strategy Configuration
 * <p>
 *
 * @param initialInterval Initial delay (milliseconds)
 * @param factor          In exponential mode, it is growth multiplier; in linear mode, it is step.
 * @param maxInterval     Maximum delay limit (milliseconds)
 * @param maxAttempts     Maximum number of attempts, unlimited attempts when maxAttempts <= 0
 * @param jitterFactor    Jitter factor (e.g., 0.1 represents ±10% random fluctuation)
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public record BackoffStrategy(
        @JsonProperty("strategy") Strategy strategy,
        @JsonProperty("timeUnit") TimeUnit timeUnit,
        @JsonProperty("initialInterval") long initialInterval,
        @JsonProperty("factor") double factor,
        @JsonProperty("maxInterval") long maxInterval,
        @JsonProperty("maxAttempts") int maxAttempts,
        @JsonProperty("jitterFactor") double jitterFactor
) {
    public enum Strategy {
        LINEAR, EXPONENTIAL
    }

    public BackoffStrategy {
        if (jitterFactor < 0 || jitterFactor > 1) {
            throw new IllegalArgumentException("Jitter factor must be between 0 and 1");
        }
    }

    public BackoffStrategy(Strategy strategy, long initialInterval, double factor, long maxInterval, int maxAttempts) {
        this(strategy, null, initialInterval, factor, maxInterval, maxAttempts, 0d);
    }

    public BackoffStrategy(Strategy strategy, long initialInterval, double factor, long maxInterval, int maxAttempts, double jitterFactor) {
        this(strategy, null, initialInterval, factor, maxInterval, maxAttempts, jitterFactor);
    }

    private long getInitialInterval() {
        return timeUnit == null ? initialInterval : timeUnit.toMillis(initialInterval);
    }

    private long getStep() {
        return timeUnit == null ? Math.round(factor) : timeUnit.toMillis(Math.round(factor));
    }

    private long getMaxInterval() {
        return timeUnit == null ? maxInterval : timeUnit.toMillis(maxInterval);
    }

    /**
     * Calculate the next delay based on the Nth attempt
     *
     * @param attempt
     * @return
     */
    public long calculateDelayByAttempt(int attempt) {
        return calculateDelayByAttempt(attempt, null);
    }

    /**
     * Calculate the next delay based on the Nth attempt
     *
     * @param attempt
     * @param targetTimeUnit
     * @return the delay in targetTimeUnit
     */
    public long calculateDelayByAttempt(int attempt, TimeUnit targetTimeUnit) {
        if (maxAttempts > 0 && attempt >= maxAttempts) return -1;

        long baseDelay = switch (this.strategy) {
            case EXPONENTIAL -> (long) (getInitialInterval() * Math.pow(factor, attempt));// Exponential formula: initial * (factor ^ attempt)
            case LINEAR -> getInitialInterval() + (long) (attempt * getStep());// Linear formula: initial + (attempt * factor)
        };


        // get upper limit
        long cappedDelay = Math.min(baseDelay, getMaxInterval());
        return applyJitter(cappedDelay, targetTimeUnit);
    }

    /**
     * Calculate the next delay based on the previous delay
     *
     * @param lastDelayWithoutJitter The base delay calculated last time (without jitter), it must be milliseconds with LINEAR strategy.
     * @return The next delay with jitter
     */
    public long calculateDelayByPrevDelay(long lastDelayWithoutJitter) {
        // If this is the first time (lastDelayWithoutJitter is 0), then use the initial interval.
        if (lastDelayWithoutJitter <= 0) {
            return applyJitter(getInitialInterval(), null);
        }

        long nextBaseDelay = switch (this.strategy) {
            case EXPONENTIAL -> (long) (lastDelayWithoutJitter * factor);
            case LINEAR -> lastDelayWithoutJitter + getStep();
        };

        // get upper limit
        nextBaseDelay = Math.min(nextBaseDelay, getMaxInterval());
        return applyJitter(nextBaseDelay, null);
    }

    private long applyJitter(long baseDelay, TimeUnit targetTimeUnit) {
        if (jitterFactor == 0) {
            return applyTimeUnit(baseDelay, targetTimeUnit);
        }
        double min = baseDelay * (1 - jitterFactor);
        double max = baseDelay * (1 + jitterFactor);
        return applyTimeUnit((long) ThreadLocalRandom.current().nextDouble(min, max), targetTimeUnit);
    }

    private long applyTimeUnit(long jitterDelay, TimeUnit targetTimeUnit) {
        if (targetTimeUnit == null) {
            return jitterDelay;
        }
        return targetTimeUnit.convert(jitterDelay, TimeUnit.MILLISECONDS);
    }
}