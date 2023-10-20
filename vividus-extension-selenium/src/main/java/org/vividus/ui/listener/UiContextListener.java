/*
 * Copyright 2019-2021 the original author or authors.
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

package org.vividus.ui.listener;

import com.google.common.eventbus.Subscribe;

import org.vividus.selenium.event.AfterWebDriverQuitEvent;
import org.vividus.selenium.event.WebDriverCreateEvent;
import org.vividus.ui.context.IUiContext;

public class UiContextListener
{
    private final IUiContext uiContext;

    public UiContextListener(IUiContext uiContext)
    {
        this.uiContext = uiContext;
    }

    @Subscribe
    public void onWebDriverCreate(WebDriverCreateEvent event)
    {
        uiContext.putSearchContext(event.getWebDriver(), () -> onWebDriverCreate(event));
    }

    @Subscribe
    public void onWebDriverQuit(@SuppressWarnings("unused") AfterWebDriverQuitEvent event)
    {
        uiContext.clear();
    }
}
