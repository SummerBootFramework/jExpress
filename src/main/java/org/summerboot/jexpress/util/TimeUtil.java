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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.summerboot.jexpress.boot.annotation.Scheduled;

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

    public static int addQuartzJob(Scheduler scheduler, Class<? extends Job> jobClass) throws SchedulerException {
        Scheduled scheduledAnnotation = (Scheduled) jobClass.getAnnotation(Scheduled.class);
        if (scheduledAnnotation == null) {
            return 0;
        }
        int dailyHour = scheduledAnnotation.dailyHour();
        int dailyMinute = scheduledAnnotation.dailyMinute();
        String[] cronExpressions = scheduledAnnotation.cron();

        ZoneId zoneId = ZoneId.systemDefault();
        String cronExpression4JobSkippedWhenDSTStarts = cronExpression4JobSkippedWhenDSTStarts(zoneId, dailyHour, dailyMinute);
        if (cronExpression4JobSkippedWhenDSTStarts != null) {
            int n = cronExpressions.length;
            String[] newArray = Arrays.copyOf(cronExpressions, n + 1);
            newArray[n] = cronExpression4JobSkippedWhenDSTStarts;
            cronExpressions = newArray;
        }
        return addQuartzJob(scheduler, jobClass, dailyHour, dailyMinute, cronExpressions);
    }

    /**
     *
     * @param scheduler
     * @param jobClass
     * @param dailyHour
     * @param dailyMinute
     * @param cronExpressions
     * @return
     * @throws SchedulerException
     */
    public static int addQuartzJob(Scheduler scheduler, Class<? extends Job> jobClass, int dailyHour, int dailyMinute, String... cronExpressions) throws SchedulerException {
        boolean hasCronJob = cronExpressions != null && cronExpressions.length > 0;
        if (!hasCronJob && dailyHour < 0) {
            return 0;
        }
        boolean hasOnly1CronJob = cronExpressions != null && cronExpressions.length == 1 && dailyHour < 0;
        boolean hasOnly1DailyJob = !hasCronJob && dailyHour >= 0;
        boolean isDurable = !(hasOnly1CronJob || hasOnly1DailyJob); // a 2nd trigger, and make job durably=true
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobClass.getName() + dailyHour + ":" + dailyMinute, jobClass.getName())
                .storeDurably(isDurable)
                .build();
        return addQuartzJob(scheduler, jobDetail, dailyHour, dailyMinute, cronExpressions);
    }

    /**
     *
     * @param scheduler
     * @param jobDetail
     * @param dailyHour
     * @param dailyMinute
     * @param cronExpressions
     * @return
     * @throws SchedulerException
     */
    public static int addQuartzJob(Scheduler scheduler, JobDetail jobDetail, int dailyHour, int dailyMinute, String... cronExpressions) throws SchedulerException {
        boolean hasCronJob = cronExpressions != null && cronExpressions.length > 0;
        boolean hasOnly1CronJob = cronExpressions != null && cronExpressions.length == 1 && dailyHour < 0;
        boolean hasOnly1DailyJob = !hasCronJob && dailyHour >= 0;
        if ((cronExpressions == null || cronExpressions.length < 1) && dailyHour < 0) {
            return 0;
        }
        int triggers = 0;
        JobKey jobKey = jobDetail.getKey();
        if (hasOnly1CronJob && !hasOnly1DailyJob) {// only one cron
            // 2. build a trigger
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpressions[0]))
                    .build();

            // 3. schedule the job with the trigger
            scheduler.scheduleJob(jobDetail, trigger);
            triggers++;
        } else if (!hasCronJob && hasOnly1DailyJob) {// only one daily
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpressions[0]))
                    .build();

            // 3. schedule the job with the trigger
            scheduler.scheduleJob(jobDetail, trigger);
            triggers++;
        } else if (hasCronJob || hasOnly1DailyJob) {
            scheduler.addJob(jobDetail, true);
            if (dailyHour >= 0) {
                CronTrigger trigger_daily_ExceptTheDayDSTStarts = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(dailyHour, dailyMinute))
                        .build();
                scheduler.scheduleJob(trigger_daily_ExceptTheDayDSTStarts);
                triggers++;
            }
            // 2b. build each trigger
            if (cronExpressions != null) {
                for (String cronExpression : cronExpressions) {
                    if (StringUtils.isBlank(cronExpression)) {
                        continue;
                    }
                    CronTrigger cronTrigger = TriggerBuilder.newTrigger()
                            .forJob(jobKey)
                            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                            .build();
                    // 3b. schedule the job with both triggers
                    scheduler.scheduleJob(cronTrigger);
                    triggers++;
                }
            }
        }
        return triggers;
    }

    /**
     * Use user specified ZoneId Not working for Israel: Friday before last
     * Sunday
     *
     * @param zoneId
     * @param hour
     * @param minute
     * @return
     */
    public static String cronExpression4JobSkippedWhenDSTStarts(ZoneId zoneId, int hour, int minute) {
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
        long dstEndMinuteOfDay = dstStartHour * 60 + durationMinutes;
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
        String dayOfWeekOption = buildQuartzDSTDayOfWeekOption(dayOfMonth);

        int quartzDayOfWeek = (dayOfWeek + 1) % 7;// 1-7: SUN-SAT
        String cronExpression4JobSkippedWhenDSTStarts = "0 " + minute + " " + (hour + durationHours) + " ? " + month + " " + quartzDayOfWeek + dayOfWeekOption;
        //String cronEuropean = "0 " + minute + " " + (hour + 1) + " ? 3 SUNL"; // In most of European Daylight Saving Time begins at 1:00 a.m. local time on the last Sunday in March
        //String cronAmerica = "0 " + minute + " " + (hour + 1) + " ? 3 SUN#2"; // In most of Canada Daylight Saving Time begins at 2:00 a.m. local time on the second Sunday in March
//        System.out.println(zoneId + ": " + cronExpression4JobSkippedWhenDSTStarts);
//        print(dstStartTransition);
        return cronExpression4JobSkippedWhenDSTStarts;
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
        return cronExpression4JobSkippedWhenDSTStarts(ZoneId.systemDefault(), hour, minute);
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
     * @param dayOfMonth
     * @return
     */
    public static String buildQuartzDSTDayOfWeekOption(int dayOfMonth) {
        String dayOfWeekOption;
        int quartzDayOfMonthIndex = BigDecimal.valueOf(dayOfMonth).divide(DAYS7, RoundingMode.CEILING).intValue();
        if (quartzDayOfMonthIndex <= 2) {// First Saturday, First Sunday, Second Sunday
            dayOfWeekOption = "#" + quartzDayOfMonthIndex;
        } else {// Last Sunday, Last Thursday, Last Friday, Last Saturday, Israel: Friday before last Sunday
            dayOfWeekOption = "L";
        }
        return dayOfWeekOption;
    }
}
