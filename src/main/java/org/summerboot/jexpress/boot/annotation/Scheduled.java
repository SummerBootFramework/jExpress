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
package org.summerboot.jexpress.boot.annotation;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * <pre>Usage: {@code
 * @Scheduled(cron="0 15 10 ? * 6L 2012-2015")// org.quartz cron expression: Fire at 10:15am on every last Friday of every month during the years 2012, 2013, 2014 and 2015
 * @Scheduled(dayOfMonth = 1, hour=2, minute=3)// monthly: every 2:03am 1st day of the month
 * @Scheduled(daysOfWeek=1, hour=14, minute=15)// weekly: 2:15pm every Sunday
 * @Scheduled(daysOfWeek={1, 6, 7}, hour=14, minute=15)// weekly: 2:15pm every Sunday, Friday and Saturday
 * @Scheduled(hour = 14, minute = 15, second = 16)// daily: 2:15:16pm everyday
 * @Scheduled(minute = 15, second = 16)// hourly: every hour at the 15th minute and the 16th second
 * @Scheduled(second = 16)// minutely: every minute at the 16th second
 * @Scheduled(fixedRateMs = 10_000, initialDelayMs=5_000)// start job after 5 seconds, run job every 10 secsonds no matter how long the job takes
 * @Scheduled(fixedDelayMs = 10_000, initialDelayMs=5_000)// start job after 5 seconds, when the job finished wait 10 seconds then start it again
 * }</pre>
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@BindingAnnotation
public @interface Scheduled {

    String[] cron() default {};

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String cronField() default "";

    /**
     * 1-31: for monthlyOnDayAndHourAndMinute(int dayOfMonth, int hour, int
     * minute)
     *
     * @return
     */
    int[] daysOfMonth() default {};

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String daysOfMonthField() default "";

    /**
     * 1-7 for SUN-SAT: for atHourAndMinuteOnGivenDaysOfWeek(int hour, int
     * minute, Integer[] daysOfWeek)
     *
     * @return
     */
    int[] daysOfWeek() default {};

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String daysOfWeekField() default "";

    /**
     * 0-23: for dailyAtHourAndMinute(int hour, int minute)
     *
     * @return
     */
    int hour() default -1;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String hourField() default "";

    /**
     * 0-59
     *
     * @return
     */
    int minute() default -1;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String minuteField() default "";

    /**
     * 0-59
     *
     * @return
     */
    int second() default -1;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String secondField() default "";

    /**
     * The fixedRate runs the scheduled task at every n millisecond. It doesn't
     * check for any previous executions of the task.
     *
     * This is useful when all executions of the task are independent. If we
     * don't expect to exceed the size of the memory and the thread pool,
     * fixedRate should be quite handy.
     *
     * Although, if the incoming tasks do not finish quickly, it's possible they
     * end up with “Out of Memory exception”.
     *
     * @return
     */
    long fixedRateMs() default 0;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String fixedRateMsField() default "";

    /**
     * The fixedDelay makes sure that there is a delay of n millisecond between
     * the finish time of an execution of a task and the start time of the next
     * execution of the task.
     *
     * This property is specifically useful when you need to make sure that only
     * one instance of the task runs all the time. For dependent jobs, it is
     * quite helpful.
     *
     *
     * @return
     */
    long fixedDelayMs() default 0;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String fixedDelayMsField() default "";

    /**
     * start job after n millisecond
     *
     * @return
     */
    long initialDelayMs() default 0;

    /**
     * The name of a static field defined in the same class, which contains a
     * configurable value
     *
     * @return
     */
    String initialDelayMsField() default "";
}
