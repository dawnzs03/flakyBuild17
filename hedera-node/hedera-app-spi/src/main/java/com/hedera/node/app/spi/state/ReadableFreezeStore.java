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

package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with freeze state
 */
public interface ReadableFreezeStore {

    /**
     * Returns the scheduled freeze time, or null if there is no freeze currently scheduled
     */
    @Nullable
    Instant freezeTime();

    /**
     * Returns the last time a freeze was successfully completed
     */
    @Nullable
    Instant lastFrozenTime();
}
