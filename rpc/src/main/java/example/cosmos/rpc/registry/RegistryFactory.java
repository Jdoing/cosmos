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
package example.cosmos.rpc.registry;

import example.cosmos.rpc.registry.impl.FileRegistryServiceImpl;

import java.util.Objects;

/**
 * The type Registry factory.
 *
 * @author slievrly
 */
public class RegistryFactory {

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static RegistryService getInstance() {
        return RegistryFactoryHolder.INSTANCE;
    }

    private static RegistryService buildRegistryService() {
        RegistryType registryType;
        String registryTypeName = RegistryType.File.name();
        try {
            registryType = RegistryType.getType(registryTypeName);
        } catch (Exception exx) {
            throw new IllegalArgumentException("not support registry type: " + registryTypeName);
        }

        return FileRegistryServiceImpl.getInstance();

    }

    private static class RegistryFactoryHolder {
        private static final RegistryService INSTANCE = buildRegistryService();
    }
}
