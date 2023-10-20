/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.config.converter;

import com.hedera.node.config.types.KeyValuePair;
import com.swirlds.config.api.converter.ConfigConverter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ConfigConverter} that supports {@link KeyValuePair} as data type for the config.
 */
public class KeyValuePairConverter implements ConfigConverter<KeyValuePair> {

    private static final String PATTERN = Pattern.quote("=");

    @Override
    public KeyValuePair convert(final String value) throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(value, "Parameter 'value' cannot be null");
        final String[] split = value.split(PATTERN, 2);
        if (split.length == 1) {
            throw new IllegalArgumentException("Invalid key value pair: " + value);
        }
        final String key = split[0];
        final String val = split[1];
        return new KeyValuePair(key, val);
    }
}
