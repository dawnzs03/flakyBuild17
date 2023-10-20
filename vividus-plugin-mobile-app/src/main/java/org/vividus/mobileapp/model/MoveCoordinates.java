/*
 * Copyright 2019-2022 the original author or authors.
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

package org.vividus.mobileapp.model;

import org.openqa.selenium.Point;

public class MoveCoordinates
{
    private final Point start;
    private final Point end;

    public MoveCoordinates(int startX, int startY, int endX, int endY)
    {
        this.start = new Point(startX, startY);
        this.end = new Point(endX, endY);
    }

    public Point getStart()
    {
        return start;
    }

    public Point getEnd()
    {
        return end;
    }
}
