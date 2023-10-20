/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.aws.outbound;

import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.Assert;

/**
 * A simple {@link MessageConverter} that delegates to a {@link Converter}.
 *
 * @author Artem Bilan
 *
 * @since 2.3
 */
class ConvertingFromMessageConverter implements MessageConverter {

	private final Converter<Object, ?> delegate;

	ConvertingFromMessageConverter(Converter<Object, ?> delegate) {
		Assert.notNull(delegate, "'delegate' must not be null");
		this.delegate = delegate;
	}

	@Override
	public Object fromMessage(Message<?> message, Class<?> targetClass) {
		return this.delegate.convert(message.getPayload());
	}

	@Override
	public Message<?> toMessage(Object payload, MessageHeaders headers) {
		throw new UnsupportedOperationException();
	}

}
