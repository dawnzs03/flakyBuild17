/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.recordings;

import org.apache.commons.lang3.tuple.Pair;

public class RecordingNotFoundException extends Exception {
    public RecordingNotFoundException(String targetId, String recordingName) {
        super(
                String.format(
                        "Recording %s was not found in the target [%s].", recordingName, targetId));
    }

    public RecordingNotFoundException(Pair<String, String> key) {
        this(key.getLeft(), key.getRight());
    }
}
