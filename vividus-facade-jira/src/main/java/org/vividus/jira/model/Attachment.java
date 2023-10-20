/*
 * Copyright 2019-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vividus.jira.model;

public class Attachment
{
    private final String name;
    private final byte[] body;

    public Attachment(String name, byte[] body)
    {
        this.name = name;
        this.body = body;
    }

    public String getName()
    {
        return name;
    }

    public byte[] getBody()
    {
        return body;
    }
}
