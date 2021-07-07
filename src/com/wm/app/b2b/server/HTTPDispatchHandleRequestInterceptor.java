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

package com.wm.app.b2b.server;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * This class will intercept the handleRequest method of HTTPDispatch of IS
 * which receives all the HTTP requests. It will create an entry span and from
 * the HTTPState (gState) of the HTTPDispatch it will capture the below details
 * and update the tags of the entry span: 1. Operation name 2. URL 3. HTTP
 * method 4. FQN 5. Stage of IS 6. Tenant ID 7. Component Name (which is the
 * Service Name set in the Agent.config)
 * 
 * In the after method it will update the child and current status from the
 * gState headers.
 * 
 * @author rika
 * 
 */
public class HTTPDispatchHandleRequestInterceptor implements InstanceMethodsAroundInterceptor {

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
		ISHttpHandlerUtils.startEntrySpan(((HTTPDispatch) objInst).gState);
	}

	@Override
	public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			Object ret) throws Throwable {
		// if the TxID is not set then we need to discard the active span
		ISHttpHandlerUtils.discardEntrySpan();
		ISHttpHandlerUtils.stopEntrySpan();
		return ret;
	}

	@Override
	public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
			Class<?>[] argumentsTypes, Throwable t) {
		ISHttpHandlerUtils.handleInterceptorException(t);
	}

}
