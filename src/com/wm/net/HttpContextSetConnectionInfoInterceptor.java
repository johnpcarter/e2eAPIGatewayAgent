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
import java.util.Enumeration;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.wm.util.Values;

/**
 * iTrack: UHM-1086
 * This class will intercept before the setConnectionInfo method of HttpContext
 * in IS is called. SW6 key was getting replaced with old SW6 key stored in tempHeaderInfo
 * This intercepter will remove the sw6 key from  tempTeaderInfo.
 * 
 * @author mali
 *
 */

public class HttpContextSetConnectionInfoInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(HttpContextSetConnectionInfoInterceptor.class);

	/**
	 * intercepting setConnectionInfo method to remove sw6 key from tempHeaderInfo
	 */
	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
		HttpContext cx = (HttpContext) objInst;
		try {
			// return if tempHeaderInfo is null
			Values hash = cx.tempHeaderInfo;
			if (hash == null)
				return;

			if (logger.isDebugEnable()) {
				String key;
				String value;
				for (Enumeration e = hash.keys(); e.hasMoreElements();) {
					key = (String) (e.nextElement());
					value = (String) (hash.get(key));

					logger.debug("******Key " + key + " Value:" + value);
				}
			}
			// remove sw6 key from tempHeaderInfo as it is already available.
			hash.remove(SW6CarrierItem.HEADER_NAME);

		} catch (Exception e) {
			logger.error("Error in ", e);
		}
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
			AbstractSpan span = ContextManager.activeSpan();
			Tags.UHM.ERROR_MSG.set(span, t.getCause().toString());
		}
	}

}
