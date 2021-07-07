package com.softwareag.e2e.agent.api;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

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
import com.softwareag.pg.rest.RestMessageContext;
import com.softwareag.uhm.constants.UhmTransactionStatus;
import com.wm.net.HttpURLConnection;

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
			

				RestMessageContext messageContext = MessageContextHolder.def.get();
				
				List<Tag> tags = messageContext.getService().getGatewayAPI().getApiDefinition().getTags();

				if (APITools.hasE2eTag(tags)) {
					
					URL u = (URL) allArguments[0];
					HttpURLConnection connection = (HttpURLConnection) objInst;

					ContextCarrier contextCarrier = new ContextCarrier();

					// create an exit span to send a call out to another IS, name of this exit span
					// does not matter as we have not excluded any span by operation name
					AbstractSpan exitSpan = ContextManager.createExitSpan(u.getPath(), contextCarrier, u.getHost() + ":" + u.getPort());
					logger.debug("Starting the exit span... " + exitSpan.getOperationName());

					SpanLayer.asHttp(exitSpan);

					contextCarrier.setParentLandscape("webMethods");
					// put all the items in contextCarrier to http header of the outgoing request
					CarrierItem next = contextCarrier.items();
					while (next.hasNext()) {
						next = next.next();
						String key = next.getHeadKey();
						String value = next.getHeadValue();
						connection.getRequestHeader().addField(key, value);
					}

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
					//ContextManager.stopSpan();
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
