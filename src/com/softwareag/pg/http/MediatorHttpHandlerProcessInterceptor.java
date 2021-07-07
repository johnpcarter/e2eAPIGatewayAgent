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
 *
 */

package com.softwareag.pg.http;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.softwareag.uhm.api.instrumentation.APIGWUtils;

/**
 * We don't need to create an entry span as it is already created by IS's HTTPDispatchHandleRequestInterceptor.
 * Check if the currently active span has TransactionID key present; if it doesn't then discard the span.
 * If the currently active span has TransactionID then add the TenantID as tag to it. 
 * 
 * @author indgo
 * 
 */
public class MediatorHttpHandlerProcessInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(MediatorHttpHandlerProcessInterceptor.class);

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
	}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		try {
			if (ContextManager.isActive()) {
				AbstractSpan activeSpan = ContextManager.activeSpan();
				if (null != activeSpan && ContextManager.isActiveSpanValid()) {
					// if TransactionID is not set (i.e, the event is not captured in APIGW) then
					// discard the current active span
					// eg, UHM-883:  add authorization policy to an API and don't not pass authorization:
					// in this case TxID will not get be created in APIGW and it is not captured in APIGW
					if (!isTransactionIDAvailable(activeSpan)) {
						ContextManager.discardActiveSpan();
					} else {
						// else set the tenantID
						Tags.UHM.TENANT_ID.set(activeSpan, APIGWUtils.getTenantID());
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error: ",  e);
		}

		return ret;
	}

	// loop through all the tags and find if it has "txn" key present & it has some value
	private boolean isTransactionIDAvailable(AbstractSpan activeSpan) {
		List<TagValuePair> tags = activeSpan.getTags();
		if (null != tags && !tags.isEmpty()) {
			for (TagValuePair tag : tags) {
				if (tag.getKey().key().equalsIgnoreCase(Tags.UHM.TRANSACTION_ID.key()) && null != tag.getValue()
						&& !tag.getValue().isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
			Class<?>[] argumentsTypes, Throwable t) {
		if (ContextManager.isActive()) {
			ContextManager.activeSpan().errorOccurred().log(t);
		}
	}
}
