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

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ConfigurationMonitor implements FileAlterationListener {

    protected static final Logger log = LogManager.getLogger(ConfigurationMonitor.class.getName());

    public static final ConfigurationMonitor cfgMonitor = new ConfigurationMonitor();

    public static final String APUSE_FILE_NAME = "pause";

    protected volatile boolean running;
    protected Map<File, Runnable> cfgUpdateTasks;
    protected FileAlterationMonitor monitor;

    protected ConfigurationMonitor() {
    }

    private static final String PAUSE_LOCK_CODE = BootConstant.PAUSE_LOCK_CODE_VIAFILE;

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

    public static void main(String[] args) throws IOException, InterruptedException {
        Path path = Paths.get("D:\\temp");
        ConfigurationMonitor.cfgMonitor.start(path, 5000L, null);
    }

    private final Map<Path, FileTime> lastModifiedTimes = new HashMap<>();
    private final Map<Path, Long> lastTriggeredTimes = new HashMap<>();

    public void start(Path folderPath, long throttleMillis, Map<File, Runnable> cfgUpdateTasks) throws IOException, InterruptedException {
        this.cfgUpdateTasks = cfgUpdateTasks;
        initPauseStatus(folderPath);

        WatchService watchService = FileSystems.getDefault().newWatchService();
        folderPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        Runnable configFolderWatcher = () -> {
            while (true) {
                WatchKey key = null;  // blocks until event occurs
                try {
                    key = watchService.take();
                } catch (InterruptedException ex) {
                    log.fatal("Failed to take configFolderWatcher@" + folderPath, ex);
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path filePath = (Path) event.context();
                    File file = new File(folderPath.toString(), filePath.toString()).getAbsoluteFile();
                    Path fullPath = file.toPath();
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == ENTRY_MODIFY) {
                        if (!Files.isRegularFile(fullPath)) {
                            continue;
                        }
                        try {
                            FileTime currentModTime = Files.getLastModifiedTime(fullPath);
                            FileTime lastTime = lastModifiedTimes.get(fullPath);
                            long now = System.currentTimeMillis();
                            Long lastTrigger = lastTriggeredTimes.getOrDefault(fullPath, 0L);

                            boolean isModified = (lastTime == null || currentModTime.compareTo(lastTime) > 0);
                            boolean isThrottled = (now - lastTrigger < throttleMillis);

                            if (isModified && !isThrottled) {
                                lastModifiedTimes.put(fullPath, currentModTime);
                                lastTriggeredTimes.put(fullPath, now);
                                onFileChange(file);
                            }
                        } catch (IOException ex) {
                            log.error("Failed to check modified time for: " + fullPath, ex);
                        }
                    } else if (kind == ENTRY_CREATE) {
                        if (Files.isRegularFile(fullPath)) {
                            onFileCreate(file);
                        } else if (Files.isDirectory(fullPath)) {
                            onDirectoryCreate(file);
                        }
                    } else if (kind == ENTRY_DELETE) {
                        if (Files.isDirectory(fullPath)) {
                            onDirectoryDelete(file);
                        } else {// Files.isRegularFile(fullPath) not working for deleted files
                            onFileDelete(file);
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        };
        BackOffice.execute(configFolderWatcher);
    }


    protected void initPauseStatus(Path folderPath) {
        File folder = folderPath.toFile();
        File pauseFile = Paths.get(folder.getAbsolutePath(), APUSE_FILE_NAME).toFile();
        boolean pause = pauseFile.exists();
        String cause;
        if (pause) {
            cause = "File detected: " + pauseFile.getAbsolutePath();
        } else {
            cause = "File not detected: " + pauseFile.getAbsolutePath();
        }
        HealthMonitor.pauseService(pause, PAUSE_LOCK_CODE, cause);
    }

    private boolean isPauseFile(File file) {
        return APUSE_FILE_NAME.equals(file.getName());
    }


    @Override
    public void onStart(FileAlterationObserver fao) {
        //log.trace(() -> "start " + fao.getDirectory());
    }

    @Override
    public void onStop(FileAlterationObserver fao) {
        //log.trace(() -> "stop " + fao.getDirectory());
    }

    @Override
    public void onDirectoryCreate(File file) {
        log.info(() -> "dir.new " + file.getAbsoluteFile());
    }

    @Override
    public void onDirectoryChange(File file) {
        //log.info(() -> "dir.mod " + file.getAbsoluteFile());
    }

    @Override
    public void onDirectoryDelete(File file) {
        log.info(() -> "dir.del " + file.getAbsoluteFile());
    }

    @Override
    public void onFileCreate(File file) {
        if (!isPauseFile(file)) {
            return;
        }
        log.info(() -> "new " + file.getAbsoluteFile());
        HealthMonitor.pauseService(true, PAUSE_LOCK_CODE, "file created " + file.getAbsolutePath());
    }

    @Override
    public void onFileDelete(File file) {
        if (!isPauseFile(file)) {
            return;
        }
        log.info(() -> "del " + file.getAbsoluteFile());
        HealthMonitor.pauseService(false, PAUSE_LOCK_CODE, "file deleted " + file.getAbsolutePath());
    }

    @Override
    public void onFileChange(File file) {
        if (isPauseFile(file)) {
            return;
        }
        log.info(() -> "mod " + file.getAbsoluteFile());
        // decouple business logic from framework logic
        // bad example: if(file.equals(AppConstant.CFG_PATH_EMAIL)){...} 
        Runnable task = cfgUpdateTasks.get(file.getAbsoluteFile());
        if (task != null) {
            task.run();
        }
    }
}
