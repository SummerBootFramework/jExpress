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

package org.summerboot.jexpress.common.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ConcurrentUtil {
    public static Timeout timeout(String processName, long timeoutMilliseconds) {
        return new Timeout(processName, timeoutMilliseconds, null, null);
    }


    /**
     * Use multi-virtual-threaded concurrent calls and wait for all calls to complete before summarizing and returning.
     * The results keep the same order as tasks, if any task throws exception, the result of that task will be null,
     * and the exception will be collected and thrown as a combined exception after all tasks have completed,
     * so that the caller can get the result of all tasks and the exceptions to all failed tasks,
     * instead of failing fast on the first exception and losing the results and exceptions to other tasks.
     *
     * @param tasks
     * @param results
     * @param <T>
     * @throws ExecutionException
     */
    public static <T> void runAndWaitForAllResults(List<Callable<T>> tasks, List<T> results) throws ExecutionException {
        int size = tasks.size();
        if (size < 1) {
            return;
        }
        List<Future<T>> futures = new ArrayList<>(size);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(task));
            }
        }  // executor.close() blocks until all submitted tasks have completed

        List<Throwable> errors = new ArrayList<>();
        for (Future<T> future : futures) {
            T result = null;
            try {
                result = future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                errors.add(ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                errors.add(cause);
            } finally {
                results.add(result);
            }
        }
        if (!errors.isEmpty()) {
            throw new ExecutionException("Execution failed on one or more tasks: " + errors, errors.get(0));
        }
    }
}
