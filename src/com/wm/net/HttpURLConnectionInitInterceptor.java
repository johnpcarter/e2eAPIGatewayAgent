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

package com.wm.net;

import java.lang.reflect.Method;
import java.net.URL;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.softwareag.uhm.constants.UhmTransactionStatus;

/**
 * This class will intercept after the init method of HttpURLConnection in IS is
 * called. This is the exit point from IS and it will create an Exit span to
 * propagate the sw6 header after the init method is executed. This exit span
 * will be closed in the same method.
 * 
 * @author rika
 *
 */

public class HttpURLConnectionInitInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(HttpURLConnectionInitInterceptor.class);

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
	}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		try {
			// if there is an active span at this point then only create a new exit span as
			// we are leaving IS process
			if (ContextManager.isActive()) {
				// only if the current span is a valid one, we will create exit span
				if (ContextManager.isActiveSpanValid()) {
					URL u = (URL) allArguments[0];
					ContextCarrier contextCarrier = new ContextCarrier();
					HttpURLConnection connection = (HttpURLConnection) objInst;

					// create an exit span to send a call out to another IS, name of this exit span
					// does not matter as we have not excluded any span by operation name
					AbstractSpan exitSpan = ContextManager.createExitSpan("HttpReqLeavingIS", contextCarrier,
							u.getHost() + ":" + u.getPort());
					logger.debug("Starting the exit span... " + exitSpan.getOperationName());

					SpanLayer.asHttp(exitSpan);

					// put all the items in contextCarrier to http header of the outgoing request
					CarrierItem next = contextCarrier.items();
					while (next.hasNext()) {
						next = next.next();
						String key = next.getHeadKey();
						String value = next.getHeadValue();
						connection.getRequestHeader().addField(key, value);
					}

					logger.debug("Stopping the exit span... " + exitSpan.getOperationName());
					/*
					 * We are closing the exit span here because the execution flow is not same for
					 * both REST and SOAP APIs in APIGW (UHM-531). The flow is
					 * 
					 * API-REST HTTPDispatchHandleRequestInterceptor -> create entry span
					 * HttpURLConnectionInitInterceptor-> Create Exit Span
					 * HttpURLConnectionDisconnectInterceptor-> Stopping the exit span
					 * BaseCollectionManagerInterceptor-> update span details in entry span
					 * HTTPDispatchHandleRequestInterceptor-> Stopping the entry span
					 * 
					 * API-SOAP HTTPDispatchHandleRequestInterceptor -> create entry span
					 * HttpURLConnectionInitInterceptor -> Create Exit Span
					 * BaseCollectionManagerInterceptor -> update span details in exit
					 * HttpURLConnectionDisconnectInterceptor -> Stopping the exit span
					 * HTTPDispatchHandleRequestInterceptor -> Stopping the entry span
					 * 
					 * Due to this the span details are coming wrong for SOAP API in APIGW.
					 */
					ContextManager.stopSpan();
				}
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
			String localizedMessage = t.getLocalizedMessage();
			if (null != localizedMessage) {
				Tags.UHM.ERROR_MSG.set(span, localizedMessage);
			}
			Tags.UHM.TRANSACTION_STATUS.set(span, UhmTransactionStatus.FAIL);
		}
	}

}
