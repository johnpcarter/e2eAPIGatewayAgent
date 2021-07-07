package com.softwareag.e2e.agent.api;

import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.named;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.description.method.MethodDescription;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher;

public class HttpURLConnectionInitInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

	private static final ILog logger = LogManager.getLogger(HttpURLConnectionInitInstrumentation.class);
	private static final String ENHANCE_CLASS = "com.wm.net.HttpURLConnection";
	private static final String ENHANCE_METHOD_PROCESS = "init";
	
	private static final String INTERCEPTOR_CLASS = "com.softwareag.e2e.agent.api.HttpURLConnectionInitInterceptor";

	@Override
	protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
		return new ConstructorInterceptPoint[0];
	}

	@Override
	protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
		return new InstanceMethodsInterceptPoint[] { new InstanceMethodsInterceptPoint() {
			@Override
			public ElementMatcher<MethodDescription> getMethodsMatcher() {
				return named(ENHANCE_METHOD_PROCESS);
			}

			@Override
			public String getMethodsInterceptor() {
				return INTERCEPTOR_CLASS;
			}

			@Override
			public boolean isOverrideArgs() {
				return false;
			}
		}
		};
	}

	@Override
	protected ClassMatch enhanceClass() {
		return NameMatch.byName(ENHANCE_CLASS);
	}
}
