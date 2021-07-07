package com.softwareag.pg.axis2.transports;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.softwareag.uhm.api.instrumentation.APIGWUtils;
import com.wm.app.b2b.server.ProtocolState;

/**
 * We don't need to create an entry span as it is already created by IS's HTTPDispatchHandleRequestInterceptor.
 * This class will handle invalid SOAP requests and discard invalid spans.
 * It will also add the TenantID to the valid span.
 * eg, when a SOAP API is imported to SOAP UI it will create a span & we don't want to capture the same.
 * 
 * @author rika
 *
 */
public class PGHTTPTransportListenerInterceptor implements InstanceMethodsAroundInterceptor {
	private static final ILog logger = LogManager.getLogger(PGHTTPTransportListenerInterceptor.class);
	private static final String SOAP_ACTION_NAME = "SOAPAction";

	@Override
	public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
			MethodInterceptResult result) throws Throwable {
		try {
			// Get the ProtocolState from arguments and get the requestHeaders from the request
			ProtocolState state = (ProtocolState) allArguments[0];
			Map<String, String> reqHeaders = state.getRequest().getHeader().getFieldsMap();
			// if requestHeaders contains "SOAPAction" key then it is a valid SOAP execution transaction
			boolean isAValidSoapCall = reqHeaders.containsKey(SOAP_ACTION_NAME);
			if (!isAValidSoapCall && ContextManager.isActive()) {
				AbstractSpan span = ContextManager.activeSpan();
				// API Soap request is not valid so discarding this span
				// eg, when a SOAP API is imported to SOAP UI it will create a span & we don't want to capture the same.
				// for this, the request header will not contain "SOAPAction" key
				if (null != span && ContextManager.isActiveSpanValid()) {
					logger.debug("Not a valid Soap request so discarding the active span: " + span.getOperationName());
					ContextManager.discardActiveSpan();
				}
			} else if (ContextManager.isActive()) {
				// else set the tenantID
				Tags.UHM.TENANT_ID.set(ContextManager.activeSpan(), APIGWUtils.getTenantID());
			}

		} catch (Exception e) {
			logger.error("Error :", e);
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
		ContextManager.activeSpan().errorOccurred().log(t);
	}

}
