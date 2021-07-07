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

package com.softwareag.pg.pgmen.collectors;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.uhm.UhmUtils;

import com.softwareag.pg.pgmen.axis2.ServiceInvokeEvent;
import com.softwareag.uhm.constants.UhmTransactionStatus;

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

public class BaseCollectionManagerInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(BaseCollectionManagerInterceptor.class);

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
		try {
			if (ContextManager.isActive()) {
				AbstractSpan span = ContextManager.activeSpan();

				// we should populate the span only if is a valid one
				if (null != span && ContextManager.isActiveSpanValid()) {
					updateSpanTagForUHM(allArguments, span);
				}
			}
		} catch (Exception e) {
			logger.error("Error in base collection manager interceptor: ", e);
		}
	}

	/**
	 * Capture all the details about the API execution and store those in UHM tags
	 * of the given span.
	 * 
	 * @param allArguments - all the arguments that were passed to publish method of
	 *                     BaseCollectionManager
	 * @param span         - current active span
	 */
	private void updateSpanTagForUHM(Object[] allArguments, AbstractSpan span) {
		ServiceInvokeEvent evt = (ServiceInvokeEvent) allArguments[0];
		
		if (evt != null) {
			// update status of this span
			if (evt.isProcessingFault()) {
				Tags.UHM.TRANSACTION_STATUS.set(span, UhmTransactionStatus.FAIL);
				Tags.UHM.ERROR_MSG.set(span, evt.getFaultMessage());

			} else {
				Tags.UHM.TRANSACTION_STATUS.set(span, UhmTransactionStatus.PASS);
			}

			Tags.UHM.OPERATION_NAME.set(span, getOpenrationName(evt));
			Tags.UHM.TRANSACTION_ID.set(span, evt.getCorrelationID());
			Tags.UHM.URL.set(span,
					evt.getGatewayDomain() + "/apigatewayui/#/analytics/summary/" + evt.getCorrelationID());
			Tags.UHM.FULLY_QUALIFIED_NAME.set(span, getFQN(evt));
			Tags.UHM.DURATION.set(span, String.valueOf(getDuration(evt)));
		}
	}

	/**
	 * Helper method to get the duration from the ServiceInvokeEvent
	 * 
	 * @param evt - the ServiceInvokeEvent from where we want to get the duration
	 * @return
	 */
	private long getDuration(ServiceInvokeEvent evt) {
		long totalElapsedMillis = evt.getTotalElapsedMillis();
		long providerRoundTripMillis = evt.getProviderRoundTripMillis();
		return totalElapsedMillis - providerRoundTripMillis;
	}

	/**
	 * Helper method to get the operation name (api name) from the
	 * ServiceInvokeEvent
	 * 
	 * @param evt - the ServiceInvokeEvent from where we want to get the operation
	 *            name
	 * @return
	 */
	private String getOpenrationName(ServiceInvokeEvent evt) {
		return evt.getApiName();
	}

	/**
	 * Helper method to get the fully qualified name (OperationName) from the
	 * ServiceInvokeEvent
	 * 
	 * @param evt - the ServiceInvokeEvent from where we want to get the fully
	 *            qualified name
	 * @return
	 */
	private String getFQN(ServiceInvokeEvent evt) {
		return UhmUtils.sanitizeFullyQualifiedName(evt.getOperationName());
	}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		return ret;
	}

	@Override
	public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
			Class<?>[] argumentsTypes, Throwable t) {
		if (ContextManager.isActive()) {
			ContextManager.activeSpan().errorOccurred().log(t);
		}
	}
}
