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
package org.summerboot.jexpress.boot.config;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ConfigurationMonitor implements FileAlterationListener {

    private static final Logger log = LogManager.getLogger(ConfigurationMonitor.class.getName());

    public static final ConfigurationMonitor listener = new ConfigurationMonitor();

    private volatile boolean running;
    private Map<File, Runnable> cfgUpdateTasks;
    private FileAlterationMonitor monitor;

    private ConfigurationMonitor() {
    }

    public void start(File folder, int intervalSec, Map<File, Runnable> cfgUpdateTasks) throws Exception {
        File pauseFile = Paths.get(folder.getAbsolutePath(), "pause").toFile();
        HealthMonitor.setPauseStatus(pauseFile.exists(), "by file detection " + pauseFile.getAbsolutePath());
        if (running) {
            return;
        }
        running = true;
        this.cfgUpdateTasks = cfgUpdateTasks;
        monitor = new FileAlterationMonitor(TimeUnit.SECONDS.toMillis(intervalSec));
        // config files
        for (File listenFile : cfgUpdateTasks.keySet()) {
            FileFilter filter = (File pathname) -> listenFile.getAbsolutePath().equals(pathname.getAbsolutePath());
            FileAlterationObserver observer = new FileAlterationObserver(folder, filter);
            observer.addListener(listener);
            monitor.addObserver(observer);
        }
        // pause trigger file
        FileFilter filter = (File pathname) -> pauseFile.getAbsolutePath().equals(pathname.getAbsolutePath());
        FileAlterationObserver observer = new FileAlterationObserver(folder, filter);
        observer.addListener(listener);
        monitor.addObserver(observer);
        // start
        monitor.start();
    }

    public void start() throws Exception {
        if (monitor != null) {
            monitor.start();
        }
    }

    public void stop() throws Exception {
        if (monitor != null) {
            monitor.stop();
        }
    }

    @Override
    public void onStart(FileAlterationObserver fao) {
        log.debug(() -> "start " + fao);
    }

    @Override
    public void onStop(FileAlterationObserver fao) {
        log.debug(() -> "stop " + fao);
    }

    @Override
    public void onDirectoryCreate(File file) {
        log.info(() -> "dir.new " + file.getAbsoluteFile());
    }

    @Override
    public void onDirectoryChange(File file) {
        log.info(() -> "dir.mod " + file.getAbsoluteFile());
    }

    @Override
    public void onDirectoryDelete(File file) {
        log.info(() -> "dir.del " + file.getAbsoluteFile());
    }

    @Override
    public void onFileCreate(File file) {
        log.info(() -> "new " + file.getAbsoluteFile());
        HealthMonitor.setPauseStatus(true, "file created " + file.getAbsolutePath());
    }

    @Override
    public void onFileChange(File file) {
        log.info(() -> "mod " + file.getAbsoluteFile());
        // decouple business logic from framework logic
        // bad example: if(file.equals(AppConstant.CFG_PATH_EMAIL)){...} 
        Runnable task = cfgUpdateTasks.get(file.getAbsoluteFile());
        if (task != null) {
            task.run();
        }
    }

    @Override
    public void onFileDelete(File file) {
        log.info(() -> "del " + file.getAbsoluteFile());
        HealthMonitor.setPauseStatus(false, "file deleted " + file.getAbsolutePath());
    }

}
