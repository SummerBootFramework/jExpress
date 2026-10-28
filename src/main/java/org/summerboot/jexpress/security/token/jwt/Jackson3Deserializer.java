/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.summerboot.jexpress.security.token.jwt;


import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.util.Map;

/**
 * REF275-1
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class Jackson3Deserializer implements Deserializer<Map<String, ?>> {

    // Use Jackson 3 ObjectMapper for deserialization
    private final ObjectMapper objectMapper;

    public Jackson3Deserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public Map<String, ?> deserialize(byte[] bytes) throws DeserializationException {
        return objectMapper.readValue(bytes, Map.class);
    }

    @Override
    public Map<String, ?> deserialize(Reader reader) throws DeserializationException {
        return objectMapper.readValue(reader, Map.class);
    }
}
