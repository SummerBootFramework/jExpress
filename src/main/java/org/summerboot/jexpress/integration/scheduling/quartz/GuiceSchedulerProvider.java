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
package org.summerboot.jexpress.integration.scheduling.quartz;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

/**
 * need to add the following into GuiceModule:
 * bind(Scheduler.class).toProvider(GuiceSchedulerProvider.class).asEagerSingleton();
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GuiceSchedulerProvider implements Provider<Scheduler> {

    @Inject
    protected Injector injector;

    @Override
    public Scheduler get() {
        try {
            StdSchedulerFactory factory = new StdSchedulerFactory();
            Scheduler scheduler = factory.getScheduler();

            scheduler.setJobFactory(new GuiceJobFactory(injector));
            scheduler.getListenerManager().addJobListener(new BootJobListener());
            return scheduler;
        } catch (SchedulerException ex) {
            throw new RuntimeException("Failed to provide a Scheduler", ex);
        }
    }

}
