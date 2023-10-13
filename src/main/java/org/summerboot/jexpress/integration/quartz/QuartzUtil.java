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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.annotation.Scheduled;
import org.summerboot.jexpress.util.TimeUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class QuartzUtil {

    public static final TimeUtil.ZoneOffsetTransitionInfo DEFAULT_DST_Transition_INFO = TimeUtil.getZoneOffsetTransitionInfo(ZoneId.systemDefault());

    /**
     *
     * @param scheduler
     * @param jobClass
     * @return number of triggers created
     * @throws SchedulerException
     */
    public static int addQuartzJob(Scheduler scheduler, Class<? extends Job> jobClass) throws SchedulerException {
        Scheduled scheduledAnnotation = (Scheduled) jobClass.getAnnotation(Scheduled.class);
        if (scheduledAnnotation == null) {
            return 0;
        }

        int[] daysOfMonth = scheduledAnnotation.daysOfMonth();
        int[] daysOfWeek = scheduledAnnotation.daysOfWeek();
        int hour = scheduledAnnotation.hour();
        int minute = scheduledAnnotation.minute();
        int second = scheduledAnnotation.second();
        String[] cronExpressions = scheduledAnnotation.cron();

        long fixedRateMs = scheduledAnnotation.fixedRateMs();
        long fixedDelayMs = scheduledAnnotation.fixedDelayMs();
        long initialDelayMs = scheduledAnnotation.initialDelayMs();
        return addQuartzJob(scheduler, jobClass, daysOfMonth, daysOfWeek, hour, minute, second, fixedRateMs, fixedDelayMs, initialDelayMs, cronExpressions);
    }

    /**
     *
     * @param scheduler
     * @param jobClass
     * @param daysOfMonth 1-31
     * @param daysOfWeek 1-7 for SUN-SAT
     * @param hour 0-23
     * @param minute 0-59
     * @param second 0-59
     * @param fixedRateMs The fixedRateMs runs the scheduled task at every n
     * millisecond
     * @param fixedDelayMs The fixedDelayMs makes sure that there is a delay of
     * n millisecond between the finish time of an execution of a task and the
     * start time of the next execution of the task
     * @param initialDelayMs start job after n millisecond
     * @param cronExpressions
     * @return number of triggers created
     * @throws SchedulerException
     */
    public static int addQuartzJob(Scheduler scheduler, Class<? extends Job> jobClass, int[] daysOfMonth, int[] daysOfWeek, Integer hour, Integer minute, Integer second, Long fixedRateMs, Long fixedDelayMs, Long initialDelayMs, String... cronExpressions) throws SchedulerException {
        boolean isFixedDelayJob = fixedDelayMs != null && fixedDelayMs > 0;
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobClass.getName(), jobClass.getName())
                .storeDurably(!isFixedDelayJob)
                .build();
        return addQuartzJob(scheduler, jobDetail, daysOfMonth, daysOfWeek, hour, minute, second, fixedRateMs, fixedDelayMs, initialDelayMs, cronExpressions);
    }

    public static final Map<Integer, String> QUARTZ_WEEKDAY_MAP = Map.of(1, "SUN", 2, "MON", 3, "TUE", 4, "WED", 5, "THU", 6, "FRI", 7, "SAT");

    private static int trim(Integer hour_minute) {
        if (hour_minute == null || hour_minute < 0) {
            return 0;
        }
        return hour_minute;
    }

    /**
     *
     * @param scheduler
     * @param jobDetail
     * @param daysOfMonth 1-31
     * @param daysOfWeek 1-7 for SUN-SAT
     * @param hour 0-23
     * @param minute 0-59
     * @param second 0-59
     * @param fixedRateMs The fixedRateMs runs the scheduled task at every n
     * millisecond
     * @param fixedDelayMs The fixedDelayMs makes sure that there is a delay of n
     * millisecond between the finish time of an execution of a task and the
     * start time of the next execution of the task
     * @param initialDelayMs start job after n millisecond
     * @param cronExpressions
     * @return number of triggers created
     * @throws org.quartz.SchedulerException
     */
    public static int addQuartzJob(final Scheduler scheduler, final JobDetail jobDetail, final int[] daysOfMonth, final int[] daysOfWeek, final Integer hour, final Integer minute, final Integer second,
            final Long fixedRateMs, final Long fixedDelayMs, final Long initialDelayMs, final String... cronExpressions) throws SchedulerException {
        boolean isCronJobs = cronExpressions != null && cronExpressions.length > 0;

        boolean isMonthlyJob = daysOfMonth != null && daysOfMonth.length > 0;
        boolean isWeeklyJob = daysOfWeek != null && daysOfWeek.length == 1;
        boolean isWeeklyJobs = daysOfWeek != null && daysOfWeek.length > 1;
        boolean isNotByDay = !isMonthlyJob && !isWeeklyJob && !isWeeklyJobs;

        boolean isDailyJob = isNotByDay && hour != null && hour >= 0;
        boolean isHourlyJob = isNotByDay && !isDailyJob && minute != null && minute >= 0;
        boolean isMinutelyJob = isNotByDay && !isDailyJob && !isHourlyJob && second != null && second >= 0;
        boolean isFixedRateJob = fixedRateMs != null && fixedRateMs > 0;
        boolean isFixedDelayJob = fixedDelayMs != null && fixedDelayMs > 0;

        if ((isMonthlyJob || isWeeklyJob || isWeeklyJobs || isDailyJob || isHourlyJob || isMinutelyJob || isCronJobs || isFixedRateJob) && isFixedDelayJob) {
            throw new SchedulerException("Unable to create Fixed Delay Job with other jobs");
        }

        Class<? extends Job> jobClass = jobDetail.getJobClass();
        String jobName = jobClass.getName();
        int triggers = 0;
        JobKey jobKey = jobDetail.getKey();
        if (jobDetail.isDurable()) {
            scheduler.addJob(jobDetail, true);
        }
        //boolean isHourJob = !isMonthlyJob && !isWeeklyJob && !isWeeklyJobs && !isDailyJob && minute >= 0;
        if (isMonthlyJob) {
            for (var dayOfMonth : daysOfMonth) {
                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withDescription(jobName + ".Monthly@" + dayOfMonth + "T" + trim(hour) + ":" + trim(minute))
                        .withSchedule(CronScheduleBuilder.monthlyOnDayAndHourAndMinute(dayOfMonth, trim(hour), trim(minute)))
                        .build();
                scheduler.scheduleJob(trigger);
                triggers++;
            }
        }
        if (isWeeklyJob) {
            int dayOfWeek = daysOfWeek[0];
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(jobName + ".Weekly@" + QUARTZ_WEEKDAY_MAP.get(dayOfWeek) + "T" + trim(hour) + ":" + trim(minute))
                    .withSchedule(CronScheduleBuilder.weeklyOnDayAndHourAndMinute(dayOfWeek, trim(hour), trim(minute)))
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;
        } else if (isWeeklyJobs) {
            Integer[] dow = Arrays.stream(daysOfWeek).boxed().toArray(Integer[]::new);
            String desc = "";
            for (int dayOfWeek : dow) {
                desc += "." + QUARTZ_WEEKDAY_MAP.get(dayOfWeek);
            }
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(jobName + ".Weekly@" + desc + "T" + trim(hour) + ":" + trim(minute))
                    .withSchedule(CronScheduleBuilder.atHourAndMinuteOnGivenDaysOfWeek(trim(hour), trim(minute), dow))
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;
        }
        if (isDailyJob) {
            String desc = jobName + ".Daily@" + hour + ":" + trim(minute);
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(hour, trim(minute)))
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;

            String cronAnnualCompensationForDSTGap = DEFAULT_DST_Transition_INFO.buildCronExpression4JobSkippedWhenDSTStarts(hour, trim(minute));
            if (cronAnnualCompensationForDSTGap != null) {
                CronTrigger cronTrigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withDescription(desc + ".Annual Compensation for Daylight Saving Time Gap@" + cronAnnualCompensationForDSTGap)
                        .withSchedule(CronScheduleBuilder.cronSchedule(cronAnnualCompensationForDSTGap))
                        .build();
                // 3b. schedule the job with both triggers
                scheduler.scheduleJob(cronTrigger);
                triggers++;
            }
        } else if (minute != null && minute >= 0) {
            String desc = jobName + ".Hourly@" + minute;
            String cron = trim(second) + " " + minute + " * * * ?";
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;
        } else if (second != null && second >= 0) {
            String desc = jobName + ".Minutely@" + second;
            String cron = second + " * * * * ?";
            CronTrigger trigger_daily_ExceptTheDayDSTStarts = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();
            scheduler.scheduleJob(trigger_daily_ExceptTheDayDSTStarts);
            triggers++;
        }
        if (isFixedRateJob) {
            long delay = initialDelayMs;
            if (initialDelayMs == null || initialDelayMs < 0) {
                delay = 0L;
            }
            Date startTime = new Date(System.currentTimeMillis() + delay);

            String desc = jobName + "@fixedRate:" + fixedRateMs + "ms, start@" + startTime;
            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(fixedRateMs)
                            .repeatForever())
                    .startAt(startTime)
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;
        }
        if (isFixedDelayJob) {
            long delay = initialDelayMs;
            if (initialDelayMs == null || initialDelayMs < 0) {
                delay = 0L;
            }
            Date startTime = new Date(System.currentTimeMillis() + delay);

            String desc = jobName + "@fixedDelay:" + fixedDelayMs + "ms, start@" + startTime;

            JobDataMap data = jobDetail.getJobDataMap();
            data.put(BootJobListener.FIXED_DELAY_VALUE, fixedDelayMs);
            data.put(BootJobListener.FIXED_DELAY_DESC, desc);
            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .startAt(startTime)
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
            triggers++;
        }

        // 2b. build each trigger
        if (isCronJobs) {
            for (String cronExpression : cronExpressions) {
                if (StringUtils.isBlank(cronExpression)) {
                    continue;
                }
                CronTrigger cronTrigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withDescription(jobName + ".Cron:" + cronExpression)
                        .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                        .build();
                // 3b. schedule the job with both triggers
                scheduler.scheduleJob(cronTrigger);
                triggers++;
            }
        }
        return triggers;
    }

    public static List<Date> getNextFireTimes(Scheduler scheduler, StringBuilder sb) throws SchedulerException {
        List<Date> ret = new ArrayList();
        Set<TriggerKey> tks = scheduler.getTriggerKeys(null);
        if (tks == null) {
            return ret;
        }
        for (TriggerKey tk : tks) {
            Trigger t = scheduler.getTrigger(tk);
            Date d = t.getNextFireTime();
            ret.add(d);
            if (sb != null) {
                sb.append(BootConstant.BR).append("\t").append(d).append(" - ").append(t.getDescription());
            }
        }
        return ret;
    }
}
