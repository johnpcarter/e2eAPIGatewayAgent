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

package com.softwareag.e2e.agent.api;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.dependencies.io.opencensus.trace.Span;

import com.softwareag.apigateway.api.model.rest.Tag;
import com.softwareag.pg.rest.RestMessageContext;


/**
 * This class will intercept the publish method of BaseCollectionManager which
 * is invoked by APIGW to publish an ServiceInvokeEvent to it's storage. From
 * the event it will capture the below details and update the tags of the
 * APIGW's entry span (which was created by
 * HTTPDispatchHandleRequestInterceptor): 1. transaction status 2. Error message
 * (in case of error) 3. Operation name 4. Transaction ID (CorrelationID) 5. FQN
 * 
 * @author rika
 * 
 */

public class MediatorHttpHandlerInterceptor implements InstanceMethodsAroundInterceptor {
	
	private static final ILog logger = LogManager.getLogger(MediatorHttpHandlerInterceptor.class);
	private static final String ERROR_MSG = "Error in ";
	
	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
		
		RestMessageContext messageContext = (RestMessageContext) allArguments[1];		
		APITools.startEntrySpan(messageContext);
		
		MessageContextHolder.def.set(messageContext);
}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		
		try {
			if (ContextManager.isActive()) {
								
				RestMessageContext messageContext = (RestMessageContext) allArguments[1];		

				if (messageContext.getService() != null) {
					List<Tag> tags = messageContext.getService().getGatewayAPI().getApiDefinition().getTags();
					
					MessageContextHolder.def.remove();

					if (APITools.hasE2eTag(tags)) {
						ContextManager.stopSpan();
					} else {
						ContextManager.discardActiveSpan();
					}	
				} 
				
				if (ContextManager.isActive()) {
					ContextManager.stopSpan();
				}
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
		
		return ret;
	}

	@Override
	public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
			Class<?>[] argumentsTypes, Throwable t) {
		
		if (ContextManager.isActive()) {
			MessageContextHolder.def.remove();
			ContextManager.activeSpan().errorOccurred().log(t);
		}
	}
}
