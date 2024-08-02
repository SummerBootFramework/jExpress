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
import org.apache.commons.lang3.StringUtils;

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
import java.util.Calendar;
import java.util.Random;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class TimeUtil {

    public static DateTimeFormatter ISO_ZONED_DATE_TIME3 = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .parseLenient()
            .appendOffset("+HH:MM", "Z")
            .toFormatter();


    public static final DateTimeFormatter ISO8601_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .parseLenient()
            .optionalStart().appendOffset("+H", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHmm", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH:mm", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HHMMss", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HH:MM:ss", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HHMMSS", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HH:MM:SS", "Z").optionalEnd()// no need
            .optionalStart().appendOffset("+HHmmss", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH:mm:ss", "Z").optionalEnd()
            //.optionalStart().appendOffsetId().optionalEnd()
            .parseStrict()
            .toFormatter();

    public static final DateTimeFormatter ISO8601_ZONED_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO8601_OFFSET_DATE_TIME)
            .optionalStart()
            .appendLiteral('[')
            .parseCaseSensitive()
            .appendZoneRegionId()
            .appendLiteral(']')
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
     * @return ET formatted time.
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
     * @param retry             the (n)th retry
     * @param truncatedMaxRetry stop(truncate) exponential backoff when after
     *                          truncatedMax retry
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

    public static class TimeDto {

        @JsonIgnoreProperties
        protected ZoneId zoneId;

        @JsonIgnoreProperties
        protected long epochTs;

        //@JsonDeserialize(using = LocalDateTimeDeserializer.class)
        //@JsonSerialize(using = LocalDateTimeSerializer.class)
        //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "YYYY-MM-DDThh:mm:ss.sTZD")//DateTimeFormatter.ISO_INSTANT
        //@JsonProperty("EffectiveDate")
        //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "YYYY-MM-DD'T'hh:mm:ss.sTZD")
        @JsonIgnoreProperties
        protected Timestamp timestamp;

        //@JsonDeserialize(using = LocalDateTimeDeserializer.class)
        //@JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonIgnoreProperties
        protected LocalDateTime localDateTime;

        @JsonIgnoreProperties
        protected OffsetDateTime offsetDateTime;

        @JsonIgnoreProperties
        protected ZonedDateTime zonedDateTime;

        public TimeDto() {
        }

        /**
         * @param epochTs
         * @param zoneIdName "America/Toronto"
         */
        public TimeDto(long epochTs, String zoneIdName) {
            this(epochTs, ZoneId.of(zoneIdName));
        }

        /**
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

    public static class ZoneOffsetTransitionInfo {

        protected final ZoneId zoneId;
        protected final ZoneOffsetTransition dstStartTransition;
        protected final int dstStartHour;
        protected final long durationMinutes;

        protected final long dstStartMinuteOfDay;
        protected final long dstEndMinuteOfDay;

        public ZoneOffsetTransitionInfo(ZoneId zoneId, ZoneOffsetTransition dstStartTransition) {
            this.zoneId = zoneId;
            this.dstStartTransition = dstStartTransition;
            this.dstStartHour = dstStartTransition.getDateTimeBefore().getHour();
            this.durationMinutes = dstStartTransition.getDuration().toMinutes();// Australia/Lord_Howe Australia/LHI: 30minutes, default: 60minutes, Antarctica/Troll: 120minutes
            this.dstStartMinuteOfDay = this.dstStartHour * 60;
            this.dstEndMinuteOfDay = this.dstStartMinuteOfDay + this.durationMinutes;
        }

        public ZoneId getZoneId() {
            return zoneId;
        }

        public ZoneOffsetTransition getDstStartTransition() {
            return dstStartTransition;
        }

        public int getDstStartHour() {
            return dstStartHour;
        }

        public long getDurationMinutes() {
            return durationMinutes;
        }

        public long getDstStartMinuteOfDay() {
            return dstStartMinuteOfDay;
        }

        public long getDstEndMinuteOfDay() {
            return dstEndMinuteOfDay;
        }

        public boolean willDailyJobBeSkippedWhenDSTStarts(int hour, int minute) {
            long jobMinuteOfDay = hour * 60 + minute;
            return jobMinuteOfDay >= dstStartMinuteOfDay && jobMinuteOfDay < dstEndMinuteOfDay;
        }

        public String buildCronExpression4JobSkippedWhenDSTStarts(int hour, int minute) {
            if (!willDailyJobBeSkippedWhenDSTStarts(hour, minute)) {
                return null;
            }
            // 3. build cron expression for a yearly cron job on DST starting day
            long durationHours = toDurationHours(durationMinutes);
            LocalDateTime dstDate = dstStartTransition.getDateTimeAfter();
            int month = dstDate.getMonthValue();// 3 = March
            int dayOfWeek = dstDate.getDayOfWeek().getValue();// 1-7: MON-SUN
            int dayOfMonth = dstDate.getDayOfMonth();// 10 = March 10
            String dayOfWeekOption = buildCronDSTDayOfWeekOption(zoneId, dayOfWeek, dayOfMonth);
            String cronExpression4JobSkippedWhenDSTStarts = "0 " + minute + " " + (hour + durationHours) + " ? " + month + " " + dayOfWeekOption;
            return cronExpression4JobSkippedWhenDSTStarts;
        }
    }

    public static ZoneOffsetTransitionInfo getZoneOffsetTransitionInfo(ZoneId zoneId) {
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
        return new ZoneOffsetTransitionInfo(zoneId, dstStartTransition);
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
        ZoneOffsetTransitionInfo dstStartTransitionInfo = getZoneOffsetTransitionInfo(zoneId);
        return dstStartTransitionInfo.buildCronExpression4JobSkippedWhenDSTStarts(hour, minute);
    }

    /**
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

    protected static final BigDecimal MINUTES60 = BigDecimal.valueOf(60);

    public static int toDurationHours(long durationMinutes) {
        return BigDecimal.valueOf(durationMinutes).divide(MINUTES60, RoundingMode.CEILING).intValue();
    }

    protected static final BigDecimal DAYS7 = BigDecimal.valueOf(7);

    /**
     * Not working for Israel: Friday before last Sunday
     *
     * @param zoneId
     * @param dayOfWeek
     * @param dayOfMonth
     * @return
     */
    public static String buildCronDSTDayOfWeekOption(ZoneId zoneId, int dayOfWeek, int dayOfMonth) {
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
}
