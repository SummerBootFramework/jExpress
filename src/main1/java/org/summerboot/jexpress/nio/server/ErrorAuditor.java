package org.summerboot.jexpress.nio.server;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface ErrorAuditor {
    String beforeSendingError(String errorContent);
}
