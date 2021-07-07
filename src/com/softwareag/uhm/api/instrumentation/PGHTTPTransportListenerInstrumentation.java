package com.softwareag.uhm.api.instrumentation;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import org.apache.skywalking.apm.dependencies.net.bytebuddy.description.method.MethodDescription;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher;
import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.named;

public class PGHTTPTransportListenerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
	private static final String ENHANCE_CLASS = "com.softwareag.pg.axis2.transports.PGHTTPTransportListener";
	private static final String ENHANCE_METHOD_PROCESS = "process";
	private static final String INTERCEPTOR_CLASS = "com.softwareag.pg.axis2.transports.PGHTTPTransportListenerInterceptor";

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
		} };
	}

	@Override
	protected ClassMatch enhanceClass() {
		return NameMatch.byName(ENHANCE_CLASS);
	}
}
