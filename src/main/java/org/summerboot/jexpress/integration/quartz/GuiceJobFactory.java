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
package org.summerboot.jexpress.integration.quartz;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.SimpleJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GuiceJobFactory extends SimpleJobFactory implements JobFactory {

    protected final Injector injector;

    public GuiceJobFactory(final Injector injector) {
        this.injector = injector;
    }

    protected final Map<Class, Job> singletonJobs = new HashMap();

    @Override
    public Job newJob(TriggerFiredBundle triggerFiredBundle, Scheduler scheduler) throws SchedulerException {
        Job job;
        // get the job class from JobDetail from TriggerFiredBundle
        Class<? extends Job> jobClass = triggerFiredBundle.getJobDetail().getJobClass();
        if (jobClass.isAnnotationPresent(Singleton.class)) {
            job = singletonJobs.get(jobClass);
            if (job == null) {
                job = newInstance(triggerFiredBundle, scheduler, jobClass);
                singletonJobs.put(jobClass, job);
            }
        } else {
            job = newInstance(triggerFiredBundle, scheduler, jobClass);
        }

        return job;
    }

    protected Job newInstance(TriggerFiredBundle triggerFiredBundle, Scheduler scheduler, Class<? extends Job> jobClass) throws SchedulerException {
        Job job = super.newJob(triggerFiredBundle, scheduler);
        if (job == null) {
            //return injector.getInstance(jobClass);
            try {
                job = jobClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                     IllegalArgumentException | InvocationTargetException ex) {
                throw new SchedulerException("Failed to create instance for " + jobClass, ex);
            }
        }
        if (job != null && injector != null) {
            injector.injectMembers(job);
        }
        return job;
    }
}
