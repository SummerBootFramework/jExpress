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
package org.summerboot.jexpress.boot.instrumentation.jmx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Singleton;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.boot.instrumentation.HTTPClientStatusListener;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.util.BeanUtil;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class ServerStatus extends NotificationBroadcasterSupport implements NIOStatusListener, HTTPClientStatusListener, ServerStatusMBean {

    protected static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;//DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm:ss");

    protected static final ExecutorService QPS_SERVICE = Executors.newSingleThreadExecutor(NamedDefaultThreadFactory.build("ServerStatus", true));

    protected final LinkedList<BootIOStatusData> events;

    public ServerStatus() {
        events = new LinkedList<>();// 1. onUpdate is called every 1 second. 2. EXEC_POOL is single thread pool
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[]{
                AttributeChangeNotification.ATTRIBUTE_CHANGE
        };

        String name = "IO Status";//AttributeChangeNotification.class.getName();
        String description = "IO event updated";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[]{info};
    }

    @Override
    public void onNIOBindNewPort(String version, String sslMode, String protocol, String bindAddr, int listeningPort, Set<String> loadBalancingEndpoints) {
        //log.info(() -> "Server " + version + " (" + sslMode + ") is listening on " + protocol + bindAddr + ":" + listeningPort + webApiContextRoot);
    }

    @Override
    public void onNIOAccessReportUpdate(String id, long hps, long tps, long totalHit, long pingHit, long bizHit, long totalChannel, long activeChannel, long task, long completed, long queue, long active, long pool, long core, long max, long largest) {
        Runnable asyncTask = () -> {
            BootIOStatusData data = new BootIOStatusData(DTF.format(LocalDateTime.now()), id, hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
            events.addFirst(data);
            while (events.size() > 100) {
                events.removeLast();
            }
            setLastIOStatus(data.toString(), id);
        };
        QPS_SERVICE.execute(asyncTask);
    }

    @Override
    public void onHTTPClientAccessReportUpdate(long task, long completed, long queue, long active, long pool, long core, long max, long largest) {
        Runnable asyncTask = () -> {
            BootIOStatusData data = new BootIOStatusData(DTF.format(LocalDateTime.now()), "HTTPClient-IO", -1, -1, -1, -1, -1, -1, -1, task, completed, queue, active, pool, core, max, largest);
            events.addFirst(data);
            while (events.size() > 100) {
                events.removeLast();
            }
            setLastIOStatus(data.toString(), "HTTPClient-IO");
        };
        QPS_SERVICE.execute(asyncTask);
    }

    protected final AtomicLong sequenceNumber = new AtomicLong(1);

    protected synchronized void setLastIOStatus(String status, String source) {
        Notification n = new AttributeChangeNotification(
                source,//this.getClass().getSimpleName(),
                sequenceNumber.getAndIncrement(),
                System.currentTimeMillis(),
                status,
                "New IO Status",
                "java.lang.String",
                "", status);
        sendNotification(n);
    }

    @Override
    public String getIOReports() {
        String ret;
        try {
            List<Object> data = events.stream().collect(Collectors.toList());
            //data.add(0, Constant.VERSION);
            ret = BeanUtil.toJson(data);
        } catch (JsonProcessingException ex) {
            ret = ex.toString();//Constant.VERSION + ": " + ex.toString();
        }
        return ret;
    }

    @Override
    public String getLastIOReport() {
        BootIOStatusData event = events.getFirst();
        return event == null ? "" : event.toString();
    }

    @Override
    public long getHealthInspector() {
        return HealthInspector.retryIndex.get();
    }

    @Override
    public String getServiceStatus() {
        return HealthMonitor.isServiceAvailable() ? "OK" : "Service Unavaliable";
    }

    @Override
    public String getServiceStatusReason() {
        return HealthMonitor.getServiceStatusReason();
    }
}
