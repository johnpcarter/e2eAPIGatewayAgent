package com.softwareag.e2e.agent.api;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.uhm.UhmUtils;

import java.net.URL;
import java.util.List;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import com.softwareag.apigateway.api.model.rest.Tag;
import com.softwareag.pg.pgmen.axis2.ServiceInvokeEvent;
import com.softwareag.pg.rest.RestMessageContext;
import com.softwareag.uhm.constants.UhmTransactionStatus;
import com.wm.net.HttpURLConnection;

public class HttpURLDisconnectionInterceptor implements InstanceMethodsAroundInterceptor {

	private static final ILog logger = LogManager.getLogger(HttpURLDisconnectionInterceptor.class);

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
										
					RestMessageContext messageContext = MessageContextHolder.def.get();
					
					List<Tag> tags = messageContext.getService().getGatewayAPI().getApiDefinition().getTags();

					if (APITools.hasE2eTag(tags)) {
						
						updateSpanTagWithAPIStuff(messageContext, (HttpURLConnection) objInst, tags, ContextManager.activeSpan());
						ContextManager.stopSpan();
					}
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

	private void updateSpanTagWithAPIStuff(RestMessageContext evt, HttpURLConnection connection, List<Tag> tags, AbstractSpan span) {
		
		if (evt != null) {
			// update status of this span

			Tags.UHM.PARENT_LANDSCAPE.set(span, "webMethods");
			Tags.UHM.COMPONENT.set(span, "API Gateway");
						
			Tags.UHM.OPERATION_NAME.set(span, evt.getServiceDisplayName());
		
			if (connection.getResponseCode() >= 300) {
				
				// report error
				
				Tags.UHM.TRANSACTION_STATUS.set(span, UhmTransactionStatus.FAIL);
				Tags.UHM.ERROR_MSG.set(span, "(" + evt.getResponseCode() + ") " + connection.getResponseMessage());
				
			} else {
				
				// report success
				
				Tags.UHM.TRANSACTION_STATUS.set(span, UhmTransactionStatus.PASS);
			}

			Tags.URL.set(span, UhmUtils.sanitizeFullyQualifiedName(connection.getURL().toString()));
			Tags.HTTP.METHOD.set(span, connection.getRequestMethod());
			
			String transIdKey = APITools.e2eTagForTransactionId(tags);
			
			if (transIdKey != null) {
				String transIdValue = APITools.e2eTransactionIdForPathParameter(transIdKey, evt);
				
				if (transIdValue != null)
					Tags.UHM.TRANSACTION_ID.set(span, transIdValue); // this was the decider for whether to discard spans in old version
			}
		}
	}
}
