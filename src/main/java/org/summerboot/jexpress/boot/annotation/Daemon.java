package org.summerboot.jexpress.boot.annotation;


import com.google.inject.BindingAnnotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@BindingAnnotation
public @interface Daemon {

    boolean ignorePause() default true;

    /**
     * Array of @Inspector.names, empty/null means ignore all HealthChecks
     *
     * @return
     */
    String[] requiredHealthChecks() default {};
}
