/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.datasource.props.synonym;

import lombok.EqualsAndHashCode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Connection property synonyms.
 */
@EqualsAndHashCode(callSuper = true)
public final class ConnectionPropertySynonyms extends PropertySynonyms {
    
    private static final Collection<String> STANDARD_PROPERTY_KEYS = new HashSet<>();
    
    static {
        STANDARD_PROPERTY_KEYS.add("dataSourceClassName");
        STANDARD_PROPERTY_KEYS.add("url");
        STANDARD_PROPERTY_KEYS.add("username");
        STANDARD_PROPERTY_KEYS.add("password");
    }
    
    public ConnectionPropertySynonyms(final Map<String, Object> props, final Map<String, String> propertySynonyms) {
        super(props, STANDARD_PROPERTY_KEYS, propertySynonyms);
    }
}
