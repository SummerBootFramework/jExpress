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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import org.summerboot.jexpress.integration.quartz.FixedDelayJobListener;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class TimeUtil {

    public static DateTimeFormatter ISO_ZONED_DATE_TIME3 = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .parseLenient()
            .appendOffset("+HH:MM", "Z")
            .toFormatter();

    public static long getSecondsSinceMidnight(Calendar c) {
        return 3600 * c.get(Calendar.HOUR_OF_DAY) + 60 * c.get(Calendar.MINUTE) + c.get(Calendar.SECOND);
    }

    public static long getSecondsTillMidnight(Calendar c) {
        return 86400 - getSecondsSinceMidnight(c);
    }

    public static int[] seconds2DHMS(long totalSeconds) {
        int[] ymdhms = {0, 0, 0, 0};
        long day = totalSeconds / 86400;
        long hour = (totalSeconds / 3600) % 24;
        long minute = (totalSeconds % 3600) / 60;
        long second = totalSeconds % 60;
        ymdhms[3] = (int) day;
        ymdhms[2] = (int) hour;
        ymdhms[1] = (int) minute;
        ymdhms[0] = (int) second;

        return ymdhms;
    }

    public static String seconds2DHMSString(long totalSeconds) {
        int[] ymdhms = seconds2DHMS(totalSeconds);
        int day = ymdhms[3];
        int hour = ymdhms[2];
        int min = ymdhms[1];
        int sec = ymdhms[0];
        return new StringBuilder()
                .append(day).append(" day").append(day > 1 ? "s " : " ")
                .append(hour).append(" hour").append(hour > 1 ? "s " : " ")
                .append(min).append(" min").append(min > 1 ? "s " : " ")
                .append(sec).append(" sec").append(sec > 1 ? "s " : " ")
                .toString();

    }

    public static DateTimeFormatter UTC_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static ZoneId ZONE_ID_ONTARIO = ZoneId.of("America/Toronto");

    /**
     * Maps the UTC time to an ET format.
     *
     * @param utcTime UTC time to be formatted.
     * @param zoneId
     *
     * @return ET formatted time.
     *
     */
    public static String utcDateTimeToLocalDateTime(String utcTime, ZoneId zoneId) {
        if (StringUtils.isBlank(utcTime)) {
            return null;
        }
        return ZonedDateTime.parse(utcTime, UTC_DATE_TIME_FORMATTER)
                .withZoneSameInstant(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    }

    public static LocalDateTime toLocalDateTime(long epochTs) {
        return toLocalDateTime(epochTs, ZoneId.systemDefault());
    }

    public static LocalDateTime toLocalDateTime(long epochTs, ZoneId zoneId) {
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        return Instant.ofEpochMilli(epochTs).atZone(zoneId).toLocalDateTime();
    }

    public static OffsetDateTime toOffsetDateTime(long epochTs, ZoneId zoneId) {
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        return Instant.ofEpochMilli(epochTs).atZone(zoneId).toOffsetDateTime();
    }

    public static ZonedDateTime toZonedDateTime(ZoneId zoneId, long epochSec, int days, int hourOfDay, int minuteOfHour, int secondOfMinute) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSec), zoneId).plusDays(days).withHour(hourOfDay).withMinute(minuteOfHour).withSecond(secondOfMinute);
    }

    public static OffsetDateTime toOffsetDateTime(LocalDate localDate, ZoneId zoneId) {
        ZonedDateTime zdt = toZonedDateTime(localDate, zoneId);
        return zdt.withZoneSameInstant(zoneId).toOffsetDateTime();
    }

    public static ZonedDateTime toZonedDateTime(LocalDate localDate, ZoneId zoneId) {
        LocalDateTime localDateTime = localDate.atStartOfDay();
        return ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
    }

    protected static Random RANDOM = new Random();

    public static int random(int low, int high) {
        int result = RANDOM.nextInt(high - low) + low;
        return result;
    }

    /**
     * expected backoff time = random value between (2^n - 1)/2 and (2^n - 1), n
     * should be truncated by max retry. For the example, E(3) = 3.5 slots, the
     * expected backoff slots should between 3 ~ 7 slots
     *
     * @param retry the (n)th retry
     * @param truncatedMaxRetry stop(truncate) exponential backoff when after
     * truncatedMax retry
     * @return the expected backoff slots
     */
    public static double truncatedExponentialBackoffSlots(int retry, int truncatedMaxRetry) {
        if (retry < 1) {
            return 0;
        }
        //1. truncate retry times
        int n = Math.min(retry, truncatedMaxRetry);
        //2. calculate expected backoff time = 2^n - 1, Changing the type of max from int to double causes the interval to be discontinuous
        int max = (1 << n) - 1;
        //3. calculate expected backoff mean
        double min = max / 2;
        //System.out.print("retry." + retry + "(" + min + "~" + max + "): ");
        //4. generate randome value between expected backoff mean and expected backoff time
        return (Math.random() * (max - min)) + min;
    }

//    public static void main(String[] args) {
//        for (int i = -2; i < 10; i++) {
//            double q = truncatedExponentialBackoffSlots(i, 5);
//            System.out.println(q);
//        }
//    }
    public static class TimeDto {

        @JsonIgnoreProperties
        private ZoneId zoneId;

        @JsonIgnoreProperties
        private long epochTs;

        //@JsonDeserialize(using = LocalDateTimeDeserializer.class)
        //@JsonSerialize(using = LocalDateTimeSerializer.class)
        //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "YYYY-MM-DDThh:mm:ss.sTZD")//DateTimeFormatter.ISO_INSTANT
        //@JsonProperty("EffectiveDate")
        //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "YYYY-MM-DD'T'hh:mm:ss.sTZD")
        @JsonIgnoreProperties
        private Timestamp timestamp;

        //@JsonDeserialize(using = LocalDateTimeDeserializer.class)
        //@JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonIgnoreProperties
        private LocalDateTime localDateTime;

        @JsonIgnoreProperties
        private OffsetDateTime offsetDateTime;

        @JsonIgnoreProperties
        private ZonedDateTime zonedDateTime;

        public TimeDto() {
        }

        /**
         *
         * @param epochTs
         * @param zoneIdName "America/Toronto"
         */
        public TimeDto(long epochTs, String zoneIdName) {
            this(epochTs, ZoneId.of(zoneIdName));
        }

        /**
         *
         * @param epochTs
         * @param zoneId
         */
        public TimeDto(long epochTs, ZoneId zoneId) {
            this.zoneId = zoneId;
            this.epochTs = epochTs;
            timestamp = new Timestamp(epochTs);
            localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTs), zoneId);        //TimeZone.getDefault().toZoneId()); 
            offsetDateTime = Instant.ofEpochMilli(epochTs).atZone(zoneId).toOffsetDateTime();
            zonedDateTime = Instant.ofEpochMilli(epochTs).atZone(zoneId);
        }

        public void sync() {
            timestamp = new Timestamp(epochTs);
            localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTs), zoneId);        //TimeZone.getDefault().toZoneId()); 
            offsetDateTime = Instant.ofEpochMilli(epochTs).atZone(zoneId).toOffsetDateTime();
            zonedDateTime = Instant.ofEpochMilli(epochTs).atZone(zoneId);
        }

        public ZoneId getZoneId() {
            return zoneId;
        }

        public void setZoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        public long getEpochTs() {
            return epochTs;
        }

        public void setEpochTs(long epochTs) {
            this.epochTs = epochTs;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Timestamp timestamp) {
            this.timestamp = timestamp;
        }

        public LocalDateTime getLocalDateTime() {
            return localDateTime;
        }

        public void setLocalDateTime(LocalDateTime localDateTime) {
            this.localDateTime = localDateTime;
        }

        public OffsetDateTime getOffsetDateTime() {
            return offsetDateTime;
        }

        public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
            this.offsetDateTime = offsetDateTime;
        }

        public ZonedDateTime getZonedDateTime() {
            return zonedDateTime;
        }

        public void setZonedDateTime(ZonedDateTime zonedDateTime) {
            this.zonedDateTime = zonedDateTime;
        }

    }

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

        long fixedRate = scheduledAnnotation.fixedRate();
        long fixedDelay = scheduledAnnotation.fixedDelay();
        long initialDelay = scheduledAnnotation.initialDelay();
        return addQuartzJob(scheduler, jobClass, daysOfMonth, daysOfWeek, hour, minute, second, fixedRate, fixedDelay, initialDelay, cronExpressions);
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
     * @param fixedRate The fixedRate runs the scheduled task at every n
     * millisecond
     * @param fixedDelay The fixedDelay makes sure that there is a delay of n
     * millisecond between the finish time of an execution of a task and the
     * start time of the next execution of the task
     * @param initialDelay start job after n millisecond
     * @param cronExpressions
     * @return number of triggers created
     * @throws SchedulerException
     */
    public static int addQuartzJob(Scheduler scheduler, Class<? extends Job> jobClass, int[] daysOfMonth, int[] daysOfWeek, Integer hour, Integer minute, Integer second, Long fixedRate, Long fixedDelay, Long initialDelay, String... cronExpressions) throws SchedulerException {
        boolean isFixedDelayJob = fixedDelay != null && fixedDelay > 0;
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobClass.getName(), jobClass.getName())
                .storeDurably(!isFixedDelayJob)
                .build();
        return addQuartzJob(scheduler, jobDetail, daysOfMonth, daysOfWeek, hour, minute, second, fixedRate, fixedDelay, initialDelay, cronExpressions);
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
     * @param fixedRate The fixedRate runs the scheduled task at every n
     * millisecond
     * @param fixedDelay The fixedDelay makes sure that there is a delay of n
     * millisecond between the finish time of an execution of a task and the
     * start time of the next execution of the task
     * @param initialDelay start job after n millisecond
     * @param cronExpressions
     * @return number of triggers created
     * @throws org.quartz.SchedulerException
     */
    public static int addQuartzJob(final Scheduler scheduler, final JobDetail jobDetail, final int[] daysOfMonth, final int[] daysOfWeek, final Integer hour, final Integer minute, final Integer second,
            final Long fixedRate, final Long fixedDelay, final Long initialDelay, final String... cronExpressions) throws SchedulerException {
        boolean isCronJobs = cronExpressions != null && cronExpressions.length > 0;

        boolean isMonthlyJob = daysOfMonth != null && daysOfMonth.length > 0;
        boolean isWeeklyJob = daysOfWeek != null && daysOfWeek.length == 1;
        boolean isWeeklyJobs = daysOfWeek != null && daysOfWeek.length > 1;
        boolean isNotByDay = !isMonthlyJob && !isWeeklyJob && !isWeeklyJobs;

        boolean isDailyJob = isNotByDay && hour != null && hour >= 0;
        boolean isHourlyJob = isNotByDay && !isDailyJob && minute != null && minute >= 0;
        boolean isMinutelyJob = isNotByDay && !isDailyJob && !isHourlyJob && second != null && second >= 0;
        boolean isFixedRateJob = fixedRate != null && fixedRate > 0;
        boolean isFixedDelayJob = fixedDelay != null && fixedDelay > 0;

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

            ZoneId zoneId = ZoneId.systemDefault();
            String cronAnnualCompensationForDSTGap = getAnnualCompensationForDSTGapCronExpression(zoneId, hour, trim(minute));
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
            long delay = initialDelay;
            if (initialDelay == null || initialDelay < 0) {
                delay = 0L;
            }
            Date startTime = new Date(System.currentTimeMillis() + delay);

            String desc = jobName + "@fixedRate:" + fixedRate + "ms, start@" + startTime;
            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withDescription(desc)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(fixedRate)
                            .repeatForever())
                    .startAt(startTime)
                    .build();
            scheduler.scheduleJob(trigger);
            triggers++;
        }
        if (isFixedDelayJob) {
            long delay = initialDelay;
            if (initialDelay == null || initialDelay < 0) {
                delay = 0L;
            }
            Date startTime = new Date(System.currentTimeMillis() + delay);

            String desc = jobName + "@fixedDelay:" + fixedDelay + "ms, start@" + startTime;

            JobDataMap data = jobDetail.getJobDataMap();
            data.put(FixedDelayJobListener.FIXED_DELAY_VALUE, fixedDelay);
            data.put(FixedDelayJobListener.FIXED_DELAY_DESC, desc);
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

    /**
     * Use system default ZoneId Not working for Israel: Friday before last
     * Sunday
     *
     * @param hour
     * @param minute
     * @return
     */
    public static String cronExpression4JobSkippedWhenDSTStarts(int hour, int minute) {
        return getAnnualCompensationForDSTGapCronExpression(ZoneId.systemDefault(), hour, minute);
    }

    /**
     * Use user specified ZoneId.
     * <p>
     * Warning: Not working for Israel: due to Friday before last Sunday in
     * March is not supported by cron syntax yet,
     * <p>
     * work-around: schedule your job every Friday in March and have some in-job
     * logic to check whether it is actually the second-to-last before going on.
     *
     * @param zoneId
     * @param hour
     * @param minute
     * @return
     */
    public static String getAnnualCompensationForDSTGapCronExpression(ZoneId zoneId, int hour, int minute) {
        if (hour < 0 || hour > 24 || minute < 0 || minute > 60) {
            return null;
        }
        if (zoneId.getId().equals("Israel")) {// TODO: pending cron syntax supports Friday before last Sunday in March at 2:00 
            return null;
        }
        // 1. get DST starting info
        ZoneOffsetTransition[] dstInfo = getDSTChangeInfo(zoneId, ZonedDateTime.now());
        ZoneOffsetTransition dstStartTransition = dstInfo[0];
        if (dstStartTransition == null) {
            return null;
        }
        boolean isFuture = dstStartTransition.getInstant().isAfter(Instant.now());
        if (!isFuture) {
            // check if DST ends in future
            ZoneOffsetTransition dstEndTransition = dstInfo[1];
            if (dstEndTransition == null) {
                return null;// DST will last forever (i.e. Ontario Bill 214)
            }
            // there still will be DST change in future, then get future DST starting info
            ZonedDateTime zdt = ZonedDateTime.ofInstant(dstEndTransition.getInstant().plusSeconds(86400), zoneId);
            dstInfo = getDSTChangeInfo(zoneId, zdt);
            dstStartTransition = dstInfo[0];
            if (dstStartTransition == null) {
                return null;// DST will never happen (i.e. Ontario Bill 214)
            }
        }

        // 2. check if hour:minute falls into the skipped DST starting interval (i.e. 2am~3am 2nd Sunday of March in Ontario)
        int dstStartHour = dstStartTransition.getDateTimeBefore().getHour();
        long durationMinutes = dstStartTransition.getDuration().toMinutes();// Australia/Lord_Howe Australia/LHI: 30minutes, default: 60minutes, Antarctica/Troll: 120minutes
        long dstStartMinuteOfDay = dstStartHour * 60;
        long dstEndMinuteOfDay = dstStartMinuteOfDay + durationMinutes;
        long jobMinuteOfDay = hour * 60 + minute;
        boolean willDailyJobNOTBeSkippedWhenDSTStarts = jobMinuteOfDay < dstStartMinuteOfDay || jobMinuteOfDay >= dstEndMinuteOfDay;
        if (willDailyJobNOTBeSkippedWhenDSTStarts) {
            return null;// job will not be skipped when DST starts, scheduled hour:minute will fall into the skipped DST starting interval (i.e. 2am~3am 2nd Sunday of March in Ontario)
        }

        // 3. build cron expression for a yearly cron job on DST starting day
        long durationHours = toDurationHours(durationMinutes);
        LocalDateTime dstDate = dstStartTransition.getDateTimeAfter();
        int month = dstDate.getMonthValue();// 3 = March
        int dayOfWeek = dstDate.getDayOfWeek().getValue();// 1-7: MON-SUN
        int dayOfMonth = dstDate.getDayOfMonth();// 10 = March 10
        String dayOfWeekOption = buildQuartzDSTDayOfWeekOption(zoneId, dayOfWeek, dayOfMonth);
        String cronExpression4JobSkippedWhenDSTStarts = "0 " + minute + " " + (hour + durationHours) + " ? " + month + " " + dayOfWeekOption;
        //String cronEuropean = "0 " + minute + " " + (hour + 1) + " ? 3 SUNL"; // In most of European Daylight Saving Time begins at 1:00 a.m. local time on the last Sunday in March
        //String cronAmerica = "0 " + minute + " " + (hour + 1) + " ? 3 SUN#2"; // In most of Canada Daylight Saving Time begins at 2:00 a.m. local time on the second Sunday in March
//        System.out.println(zoneId + ": " + getAnnualCompensationForDSTGapCronExpression);
//        print(dstStartTransition);
        return cronExpression4JobSkippedWhenDSTStarts;
    }

    /**
     *
     * @param zoneId
     * @param zdt
     * @return Two elements: array[0] is DST starting info or null if DST is not
     * applied, array[1] is DST ending info or null if DST is not applied
     */
    public static ZoneOffsetTransition[] getDSTChangeInfo(ZoneId zoneId, ZonedDateTime zdt) {
        ZoneRules zoneRules = zoneId.getRules();
        //ZonedDateTime zdt = ZonedDateTime.now();//ZonedDateTime.of(2017, 1, 1, 10, 0, 0, 0, zoneId);//ZonedDateTime.now();
        Instant instant = zdt.toInstant();

        ZoneOffsetTransition prevTransition = zoneRules.previousTransition(instant);
        ZoneOffsetTransition nextTransition = zoneRules.nextTransition(instant);

        ZoneOffsetTransition dstStartTransition = null, dstEndTransition = null;
        if (prevTransition != null) {
            if (zoneRules.isDaylightSavings(prevTransition.getInstant())) {
                dstStartTransition = prevTransition;
            } else {
                dstEndTransition = prevTransition;
            }
        }
        if (nextTransition != null) {
            if (zoneRules.isDaylightSavings(nextTransition.getInstant())) {
                dstStartTransition = nextTransition;
            } else {
                dstEndTransition = nextTransition;
            }
        }
        //print(transition);
        ZoneOffsetTransition[] ret = {dstStartTransition, dstEndTransition};
        return ret;
    }

    public static void print(ZoneOffsetTransition transition) {
        if (transition == null) {
            return;
        }
        int dstStartHour = transition.getDateTimeBefore().getHour();
        long durationMinutes = transition.getDuration().toMinutes();
        boolean isFuture = transition.getInstant().isAfter(Instant.now());

        System.out.println("\ttransition.isFuture=" + isFuture);
        System.out.println("\ttransition.getInstant=" + transition.getInstant());
        System.out.println("\ttransition.getDateTimeBefore=" + transition.getDateTimeBefore());
        System.out.println("\ttransition.getDateTimeAfter=" + transition.getDateTimeAfter());
        System.out.println("\ttransition.dstStartHour=" + dstStartHour + "am");
        System.out.println("\ttransition.getDuration=" + durationMinutes + "minutes");
        System.out.println("\ttransition.getOffsetBefore=" + transition.getOffsetBefore());
        System.out.println("\ttransition.getOffsetAfter=" + transition.getOffsetAfter());
        System.out.println("\ttransition.isGap=" + transition.isGap());
        System.out.println("\ttransition.isOverlap=" + transition.isOverlap());
    }

    private static final BigDecimal MINUTES60 = BigDecimal.valueOf(60);

    public static int toDurationHours(long durationMinutes) {
        return BigDecimal.valueOf(durationMinutes).divide(MINUTES60, RoundingMode.CEILING).intValue();
    }

    private static final BigDecimal DAYS7 = BigDecimal.valueOf(7);

    /**
     * Not working for Israel: Friday before last Sunday
     *
     * @param zoneId
     * @param dayOfWeek
     * @param dayOfMonth
     * @return
     */
    public static String buildQuartzDSTDayOfWeekOption(ZoneId zoneId, int dayOfWeek, int dayOfMonth) {
        String dayOfWeekOption;
//        if (zoneId.getId().equals("Israel")) {
//            dayOfWeekOption = "1L-2";// TODO: pending cron syntax supports Friday before last Sunday in March at 2:00 
//        } else {
        int quartzDayOfWeek = (dayOfWeek + 1) % 7;// 1-7: SUN-SAT
        int quartzDayOfMonthIndex = BigDecimal.valueOf(dayOfMonth).divide(DAYS7, RoundingMode.CEILING).intValue();
        if (quartzDayOfMonthIndex <= 2) {// First Saturday, First Sunday, Second Sunday
            dayOfWeekOption = quartzDayOfWeek + "#" + quartzDayOfMonthIndex;
        } else {// Last Sunday, Last Thursday, Last Friday, Last Saturday, Israel: Friday before last Sunday
            dayOfWeekOption = quartzDayOfWeek + "L";
        }
//        }

        return dayOfWeekOption;
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
