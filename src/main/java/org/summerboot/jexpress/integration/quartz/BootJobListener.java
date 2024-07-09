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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import java.util.Date;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootJobListener extends JobListenerSupport {

    protected static final Logger log = LogManager.getLogger(BootJobListener.class);

    public static final String FIXED_DELAY_VALUE = "jExpress_FIXED_DELAY_VALUE";
    public static final String FIXED_DELAY_DESC = "jExpress_FIXED_DELAY_DESC";
    protected static final String JOB_LISTENER_NAME = BootJobListener.class.getSimpleName();

    @Override
    public String getName() {
        return JOB_LISTENER_NAME;
    }

    @Override
    public void jobWasExecuted(final JobExecutionContext context, final JobExecutionException exception) {
        scheduleFixedDelayJob(context, exception);
        scheduleDSTDailyJob(context, exception);
        logNextFireTime(context, exception);
    }

    protected Date logNextFireTime(final JobExecutionContext context, final JobExecutionException exception) {
        Date nextFireTime = context.getNextFireTime();
        if (nextFireTime != null && log.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Scheduled jobs next fire time by triggers: ");
            try {
                Scheduler scheduler = context.getScheduler();
                QuartzUtil.getNextFireTimes(scheduler, sb);
            } catch (Throwable ex) {
                sb.append(ex);
            }
            log.info(() -> sb.toString());
        }
        return nextFireTime;
    }

    protected void scheduleFixedDelayJob(final JobExecutionContext context, final JobExecutionException exception) {
        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();
        if (!jobData.containsKey(FIXED_DELAY_VALUE)) {
            log.info("Scheduled jobs next fire time by triggers: none");
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
                SimpleTrigger st = (SimpleTrigger) currentTrigger;// TODO: JDK17
                if (st.getRepeatInterval() != 0 || st.getRepeatCount() != 0 || st.getTimesTriggered() != 1 || st.getNextFireTime() != null) {
                    return;
                }
            }
            long fixedDelayMs = (long) jobData.getWrappedMap().get(FIXED_DELAY_VALUE);
            String desc = (String) jobData.getWrappedMap().get(FIXED_DELAY_DESC);
            Date nextTime = new Date(System.currentTimeMillis() + fixedDelayMs);
            JobKey jobKey = jobDetail.getKey();
            Trigger nextTrigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withIdentity(currentTriggerKey)
                    .startAt(nextTime)
                    .build();
            scheduler.rescheduleJob(currentTriggerKey, nextTrigger);
            log.info(desc + " scheduled@" + nextTime);
        } catch (SchedulerException ex) {
            log.error("failed to reschedule the job with triger: {}", currentTriggerKey, ex);
        }
    }

    protected void scheduleDSTDailyJob(final JobExecutionContext context, final JobExecutionException exception) {
        //TODO:
    }
}
