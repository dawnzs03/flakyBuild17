/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.threading;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.swirlds.common.test.fixtures.threading.ExceptionSuppressingParallelExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExceptionSuppressingParallelExecutorTests {

    @Test
    @DisplayName("Test exception suppressed")
    void testExceptionsSuppressed() {
        ExceptionSuppressingParallelExecutor executor =
                new ExceptionSuppressingParallelExecutor(getStaticThreadManager());
        executor.start();

        assertDoesNotThrow(
                () -> executor.doParallel(
                        () -> {
                            throw new NullPointerException();
                        },
                        () -> null),
                "Exceptions from task 1 should be suppressed.");

        assertDoesNotThrow(
                () -> executor.doParallel(() -> null, () -> {
                    throw new NullPointerException();
                }),
                "Exceptions from task 2 should be suppressed.");
    }
}
