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

import java.util.Date;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.listeners.JobListenerSupport;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class FixedDelayJobListener extends JobListenerSupport {

    public static final String FIXED_DELAY_VALUE = "FIXED_DELAY_VALUE";
    public static final String FIXED_DELAY_DESC = "FIXED_DELAY_DESC";
    private static final String JOB_LISTENER_NAME = "FixedDelayJobListener";

    @Override
    public String getName() {
        return JOB_LISTENER_NAME;
    }

    @Override
    public void jobWasExecuted(final JobExecutionContext context, final JobExecutionException exception) {
        Date nextFireTime = context.getNextFireTime();
        if (nextFireTime != null) {
            return;
        }
        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();
        if (!jobData.containsKey(FIXED_DELAY_VALUE)) {
            return;
        }

        TriggerKey currentTriggerKey = context.getTrigger().getKey();
        try {
            Scheduler scheduler = context.getScheduler();
            if (scheduler.isShutdown()) {
                return;
            }

            Trigger currentTrigger = context.getTrigger();
            if (currentTrigger instanceof SimpleTrigger) {
                SimpleTrigger st = (SimpleTrigger) currentTrigger;
                if (st.getRepeatInterval() != 0 || st.getRepeatCount() != 0 || st.getTimesTriggered() != 1 || st.getNextFireTime() != null) {
                    return;
                }
            }
            long fixedDelay = (long) jobData.getWrappedMap().get(FIXED_DELAY_VALUE);
            String desc = (String) jobData.getWrappedMap().get(FIXED_DELAY_DESC);
            Date nextTime = new Date(System.currentTimeMillis() + fixedDelay);
            JobKey jobKey = jobDetail.getKey();
            Trigger nextTrigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withIdentity(currentTriggerKey)
                    .startAt(nextTime)
                    .build();
            scheduler.rescheduleJob(currentTriggerKey, nextTrigger);
            getLog().info(desc + " scheduled@" + nextTime);

        } catch (SchedulerException ex) {
            getLog().error("failed to reschedule the job with triger: {}", currentTriggerKey, ex);
        }
    }
}
