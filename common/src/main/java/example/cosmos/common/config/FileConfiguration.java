/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package example.cosmos.common.config;


import example.cosmos.common.CollectionUtils;
import example.cosmos.common.ConfigurationKeys;
import example.cosmos.common.StringUtils;
import example.cosmos.common.config.file.FileConfig;
import example.cosmos.common.thread.NamedThreadFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * The type FileConfiguration.
 *
 * @author slievrly
 */
public class FileConfiguration extends AbstractConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileConfiguration.class);

    private FileConfig fileConfig;

    private ExecutorService configOperateExecutor;

    private static final int CORE_CONFIG_OPERATE_THREAD = 1;

    private static final int MAX_CONFIG_OPERATE_THREAD = 2;

    private static final long LISTENER_CONFIG_INTERVAL = 1 * 1000;

    private static final String REGISTRY_TYPE = "file";

    public static final String SYS_FILE_RESOURCE_PREFIX = "file:";

    private final ConcurrentMap<String, Set<ConfigurationChangeListener>> configListenersMap = new ConcurrentHashMap<>(
            8);

    private final Map<String, String> listenedConfigMap = new HashMap<>(8);

    private final String targetFilePath;

    private volatile long targetFileLastModified;

    private final String name;

    private final FileListener fileListener = new FileListener();

    private final boolean allowDynamicRefresh;
    private static final String NAME_KEY = "name";
    private static final String FILE_TYPE = "file";
    private static String pathDataId = String.join(ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR,
            ConfigurationKeys.FILE_ROOT_CONFIG, FILE_TYPE, NAME_KEY);

    public FileConfiguration() {
        this(pathDataId);
    }

    /**
     * Instantiates a new File configuration.
     *
     * @param name the name
     */
    public FileConfiguration(String name) {
        this(name, true);
    }

    /**
     * Instantiates a new File configuration.
     * For seata-server side the conf file should always exists.
     * For application(or client) side,conf file may not exists when using seata-spring-boot-starter
     * @param name                the name
     * @param allowDynamicRefresh the allow dynamic refresh
     */
    public FileConfiguration(String name, boolean allowDynamicRefresh) {
        File file = getConfigFile(name);
        if (file == null) {
            targetFilePath = null;
            fileConfig = FileConfigFactory.load();
            this.allowDynamicRefresh = false;
        } else {
            targetFilePath = file.getPath();
            fileConfig = FileConfigFactory.load(file, name);
            targetFileLastModified = new File(targetFilePath).lastModified();
            this.allowDynamicRefresh = allowDynamicRefresh;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("The file name of the operation is {}", name);
            }
        }
        this.name = name;
        configOperateExecutor = new ThreadPoolExecutor(CORE_CONFIG_OPERATE_THREAD, MAX_CONFIG_OPERATE_THREAD,
                Integer.MAX_VALUE, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                new NamedThreadFactory("configOperate", MAX_CONFIG_OPERATE_THREAD));
    }

    private File getConfigFile(String name) {
        try {
            if (name == null) {
                throw new IllegalArgumentException("name can't be null");
            }

            boolean filePathCustom = name.startsWith(SYS_FILE_RESOURCE_PREFIX);
            String filePath = filePathCustom ? name.substring(SYS_FILE_RESOURCE_PREFIX.length()) : name;
            String decodedPath = URLDecoder.decode(filePath, StandardCharsets.UTF_8.name());

            File targetFile = getFileFromFileSystem(decodedPath);
            if (targetFile != null) {
                return targetFile;
            }

            if (!filePathCustom) {
                targetFile = getFileFromClasspath(name);
                if (targetFile != null) {
                    return targetFile;
                }
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("decode name error: {}", e.getMessage(), e);
        }

        return null;
    }

    private File getFileFromFileSystem(String decodedPath) {

        // run with jar file and not package third lib into jar file, this.getClass().getClassLoader() will be null
        URL resourceUrl = this.getClass().getClassLoader().getResource("");
        String[] tryPaths = null;
        if (resourceUrl != null) {
            tryPaths = new String[]{
                // first: project dir
                resourceUrl.getPath() + decodedPath,
                // second: system path
                decodedPath
            };
        } else {
            tryPaths = new String[]{
                decodedPath
            };
        }


        for (String tryPath : tryPaths) {
            File targetFile = new File(tryPath);
            if (targetFile.exists()) {
                return targetFile;
            }

            // try to append config suffix
            for (String s : FileConfigFactory.getSuffixSet()) {
                targetFile = new File(tryPath + ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR + s);
                if (targetFile.exists()) {
                    return targetFile;
                }
            }
        }

        return null;
    }

    private File getFileFromClasspath(String name) throws UnsupportedEncodingException {
        URL resource = this.getClass().getClassLoader().getResource(name);
        if (resource == null) {
            for (String s : FileConfigFactory.getSuffixSet()) {
                resource = this.getClass().getClassLoader().getResource(name + ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR + s);
                if (resource != null) {
                    String path = resource.getPath();
                    path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
                    return new File(path);
                }
            }
        } else {
            String path = resource.getPath();
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            return new File(path);
        }

        return null;
    }

    @Override
    public String getLatestConfig(String dataId, String defaultValue, long timeoutMills) {
        String value = getConfigFromSys(dataId);
        if (value != null) {
            return value;
        }
        ConfigFuture configFuture = new ConfigFuture(dataId, defaultValue, ConfigFuture.ConfigOperation.GET, timeoutMills);
        configOperateExecutor.submit(new ConfigOperateRunnable(configFuture));
        Object getValue = configFuture.get();
        return getValue == null ? null : String.valueOf(getValue);
    }

    @Override
    public boolean putConfig(String dataId, String content, long timeoutMills) {
        ConfigFuture configFuture = new ConfigFuture(dataId, content, ConfigFuture.ConfigOperation.PUT, timeoutMills);
        configOperateExecutor.submit(new ConfigOperateRunnable(configFuture));
        return (Boolean) configFuture.get();
    }

    @Override
    public boolean putConfigIfAbsent(String dataId, String content, long timeoutMills) {
        ConfigFuture configFuture = new ConfigFuture(dataId, content, ConfigFuture.ConfigOperation.PUTIFABSENT, timeoutMills);
        configOperateExecutor.submit(new ConfigOperateRunnable(configFuture));
        return (Boolean) configFuture.get();
    }

    @Override
    public boolean removeConfig(String dataId, long timeoutMills) {
        ConfigFuture configFuture = new ConfigFuture(dataId, null, ConfigFuture.ConfigOperation.REMOVE, timeoutMills);
        configOperateExecutor.submit(new ConfigOperateRunnable(configFuture));
        return (Boolean) configFuture.get();
    }

    @Override
    public void addConfigListener(String dataId, ConfigurationChangeListener listener) {

    }

    @Override
    public void removeConfigListener(String dataId, ConfigurationChangeListener listener) {
        if (StringUtils.isBlank(dataId) || listener == null) {
            return;
        }
        Set<ConfigurationChangeListener> configListeners = getConfigListeners(dataId);
        if (CollectionUtils.isNotEmpty(configListeners)) {
            configListeners.remove(listener);
            if (configListeners.isEmpty()) {
                configListenersMap.remove(dataId);
                listenedConfigMap.remove(dataId);
            }
        }
        listener.onShutDown();
    }

    @Override
    public Set<ConfigurationChangeListener> getConfigListeners(String dataId) {
        return configListenersMap.get(dataId);
    }

    @Override
    public String getTypeName() {
        return REGISTRY_TYPE;
    }

    /**
     * The type Config operate runnable.
     */
    class ConfigOperateRunnable implements Runnable {

        private ConfigFuture configFuture;

        /**
         * Instantiates a new Config operate runnable.
         *
         * @param configFuture the config future
         */
        public ConfigOperateRunnable(ConfigFuture configFuture) {
            this.configFuture = configFuture;
        }

        @Override
        public void run() {
            if (configFuture != null) {
                if (configFuture.isTimeout()) {
                    setFailResult(configFuture);
                    return;
                }
                try {
                    if (allowDynamicRefresh) {
                        long tempLastModified = new File(targetFilePath).lastModified();
                        if (tempLastModified > targetFileLastModified) {
                            FileConfig tempConfig = FileConfigFactory.load(new File(targetFilePath), name);
                            if (tempConfig != null) {
                                fileConfig = tempConfig;
                                targetFileLastModified = tempLastModified;
                            }
                        }
                    }
                    if (configFuture.getOperation() == ConfigFuture.ConfigOperation.GET) {
                        String result = fileConfig.getString(configFuture.getDataId());
                        configFuture.setResult(result);
                    } else if (configFuture.getOperation() == ConfigFuture.ConfigOperation.PUT) {
                        //todo
                        configFuture.setResult(Boolean.TRUE);
                    } else if (configFuture.getOperation() == ConfigFuture.ConfigOperation.PUTIFABSENT) {
                        //todo
                        configFuture.setResult(Boolean.TRUE);
                    } else if (configFuture.getOperation() == ConfigFuture.ConfigOperation.REMOVE) {
                        //todo
                        configFuture.setResult(Boolean.TRUE);
                    }
                } catch (Exception e) {
                    setFailResult(configFuture);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Could not found property {}, try to use default value instead. exception:{}",
                                configFuture.getDataId(), e.getMessage());
                    }
                }
            }
        }

        private void setFailResult(ConfigFuture configFuture) {
            if (configFuture.getOperation() == ConfigFuture.ConfigOperation.GET) {
                String result = configFuture.getContent();
                configFuture.setResult(result);
            } else {
                configFuture.setResult(Boolean.FALSE);
            }
        }

    }

    /**
     * The type FileListener.
     */
    class FileListener implements ConfigurationChangeListener {

        private final Map<String, Set<ConfigurationChangeListener>> dataIdMap = new HashMap<>();

        private final ExecutorService executor = new ThreadPoolExecutor(CORE_LISTENER_THREAD, MAX_LISTENER_THREAD, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                new NamedThreadFactory("fileListener", MAX_LISTENER_THREAD));

        /**
         * Instantiates a new FileListener.
         */
        FileListener() {}

        public synchronized void addListener(String dataId, ConfigurationChangeListener listener) {
            // only the first time add listener will trigger on process event
            if (dataIdMap.isEmpty()) {
                fileListener.onProcessEvent(new ConfigurationChangeEvent());
            }

            dataIdMap.computeIfAbsent(dataId, value -> new HashSet<>()).add(listener);
        }

        @Override
        public void onChangeEvent(ConfigurationChangeEvent event) {

        }

        @Override
        public ExecutorService getExecutorService() {
            return executor;
        }
    }

}
