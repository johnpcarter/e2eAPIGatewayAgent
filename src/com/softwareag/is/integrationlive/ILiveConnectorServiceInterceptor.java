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

package com.softwareag.is.integrationlive;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.uhm.UhmUtils;

import org.apache.skywalking.apm.dependencies.com.google.common.base.Strings;
import com.softwareag.util.IDataMap;
import com.wm.app.b2b.server.InvokeState;

/**
 * Start an exit span to propagate the sw6 header in the outgoing request from
 * IC. It will intercept the
 * com.softwareag.is.integrationlive.ILiveConnectorService.createConnectionProperties(IntegrationServerConnection)
 * method and add the SW6 header, tenantId and stage to the
 * IDataMap-ConnectionProperties. This sw6 header will be propagated by the axis
 * engine as a part of MessageContext.TRANSPORT_HEADERS. Stop the exit span
 * after it.
 * 
 * 
 *
 */
public class ILiveConnectorServiceInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(ILiveConnectorServiceInterceptor.class);

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
	}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		try {
			logger.info("Starting the exit span...ContextManager.isActive() " + ContextManager.isActive());
			if (ContextManager.isActive()) {
				// only if the current span is a valid one, we will create exit span
//				if (ContextManager.isActiveSpanValid()) {
				logger.info("Starting the exit span...ContextManager.isActiveSpanValid() " + ContextManager.isActiveSpanValid());
					ContextCarrier contextCarrier = new ContextCarrier();

					AbstractSpan exitSpan = ContextManager.createExitSpan("CLOUD_TO_ONPREM_CHANNEL", contextCarrier,
							((IDataMap) ret).get("$requestToOnPremiseQueueName").toString());
					logger.info("Starting the exit span... " + exitSpan.getOperationName());

					SpanLayer.asHttp(exitSpan);

					((IDataMap) ret).put(SW6CarrierItem.HEADER_NAME,
							UhmUtils.extractTracingHeaderFromCarrier(contextCarrier));
					((IDataMap) ret).put("tenantId", InvokeState.getCurrentState().getTenantID());
					((IDataMap) ret).put("stageId", InvokeState.getCurrentState().getStageID());
					logger.info("Exit span... SW6CarrierItem.HEADER_NAME" + UhmUtils.extractTracingHeaderFromCarrier(contextCarrier));
					logger.info("Exit span... stageId" + InvokeState.getCurrentState().getStageID());
					logger.info("Exit span... tenantId" + InvokeState.getCurrentState().getTenantID());
					ContextManager.stopSpan();
//				}
			}
		} catch (Exception e) {
			logger.error("Error in ", e);
		}

		return ret;
	}

	@Override
	public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
			Class<?>[] argumentsTypes, Throwable t) {
		if (ContextManager.isActive()) {
			ContextManager.activeSpan().errorOccurred().log(t);
			AbstractSpan span = ContextManager.activeSpan();
			if (!Strings.isNullOrEmpty(t.getLocalizedMessage())) {
				logger.error("Error captured in is plugin: " + t.getLocalizedMessage());
				StringBuilder localizedMessage = new StringBuilder();
				localizedMessage.append(t.getLocalizedMessage());
				Tags.UHM.ERROR_MSG.set(span, localizedMessage.toString());
			}
		}
	}
}
