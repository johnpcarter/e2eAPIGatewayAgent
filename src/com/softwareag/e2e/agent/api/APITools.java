package com.softwareag.e2e.agent.api;

import static java.util.Objects.nonNull;

import java.util.List;
import java.util.Map;

import org.apache.axis2.addressing.EndpointReference;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.uhm.UhmUtils;
import org.apache.skywalking.apm.agent.core.utils.CollectionUtils;
import org.apache.skywalking.apm.dependencies.com.google.common.base.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.softwareag.apigateway.api.model.Endpoint;
import com.softwareag.apigateway.api.model.rest.Tag;
import com.softwareag.pg.rest.RestMessageContext;
import com.wm.app.b2b.server.ISHttpHandlerUtils;
import com.wm.app.b2b.server.InvokeState;
import com.wm.net.EncodeURL;

public class APITools {

	private static final String E2E_LABEL = "e2e";

	private static final ILog logger = LogManager.getLogger(ISHttpHandlerUtils.class);
	/**
	 * This is set on uhm dev, test machines to set tenantId.
	 */
	private static final boolean DEV_MODE = Boolean.parseBoolean(System.getProperty("UHM_DEV_MODE", "false"));
	/**
	 * This is set on local laptops of developers to generate any trace data.
	 */
	private static final boolean DEVELOPER_MODE = Boolean
			.parseBoolean(System.getProperty("UHM_DEVELOPER_MODE", "false"));
	private static final String UHM_DEFAULT_TENANT_ID = System.getProperty("UHM_DEFAULT_TENANT_ID", "");
	private static final String WMIC_EXECUTION_PARAM_HEADER_KEY = "X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON";
	private static final String X_WMIC_SUBDOMAIN_HEADER_KEY = "X-WMIC-SUBDOMAIN";
	private static final String ERROR_MSG = "Error in ";

	/**
	 * Handles any interception exception.
	 * 
	 * @param t
	 */
	public static void handleInterceptorException(Throwable t) {
		try {
			if (ContextManager.isActive()) {
				ContextManager.activeSpan().errorOccurred().log(t);
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}

	/**
	 * Stops the active entry span.
	 */
	public static void stopEntrySpan() {
		try {
			if (ContextManager.isActive()) {
				// always stop the span, no matter what type
				ContextManager.stopSpan();
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}

	/**
	 * Discards the active entry span.
	 */
	public static void discardEntrySpan() {
		try {
			// if the TxID is not set then we need to discard this span and this TxID should
			// be set
			// for WMIC-IS by EventEmitter.emitEvent()
			// for API by BaseCollectionManager.publish()
			if (!DEVELOPER_MODE && ContextManager.isActive() && nonNull(ContextManager.activeSpan())
					&& !APITools.isTransactionIDAvailable(ContextManager.activeSpan())) {
				ContextManager.discardActiveSpan();
			}
		} catch (Exception e) {
			logger.error(ERROR_MSG, e);
		}
	}

	/**
	 * Helper method to find if TransactionID is available in a span. It will loop
	 * through all the tags and find if it has "txn" key present & it has some value
	 * 
	 * @param activeSpan - currently active span
	 * @return
	 */
	private static boolean isTransactionIDAvailable(AbstractSpan activeSpan) {
		
		List<TagValuePair> tags = activeSpan.getTags();
		
		if (CollectionUtils.isNotEmpty(tags)) {
			for (TagValuePair tag : tags) {
				if (tag.getKey().key().equalsIgnoreCase(Tags.UHM.TRANSACTION_ID.key()) && null != tag.getValue()
						&& !tag.getValue().isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Starts an entry span from the HTTPState
	 * 
	 * @param gState - current HTTPState
	 */
	public static ContextCarrier startEntrySpan(RestMessageContext context) {
		try {
			ContextCarrier contextCarrier = createContextCarrier(context);

			// start a entry span to denote that we have entered into API Gateway
			AbstractSpan span = ContextManager.createEntrySpan("API Gateway", contextCarrier);
			// only if a valid span is created, we should populate the rest of span
			if (APITools.isActiveSpanValid()) {
				logger.debug("Created the Entry span..." + span.getOperationName());

				populateSpanData(context, span);

				SpanLayer.asHttp(span);
			}
			
			return contextCarrier;
			
		} catch (Exception t) {
			logger.error("Error :", t);
			return null;
		}
	}
	
	public static boolean startExitSpan(RestMessageContext context, ContextCarrier contextCarrier) {
		
		try {
			
			// start a entry span to denote that we have entered into IS
			String opName = getOperationName(context);
			AbstractSpan span = ContextManager.createExitSpan(opName, contextCarrier, getNativeUrlFromContext(context));
			
			// only if a valid span is created, we should populate the rest of span
			if (APITools.isActiveSpanValid()) {
				logger.debug("Created the Exit span..." + span.getOperationName());

				contextCarrier.setParentLandscape("webMethods");

				populateSpanData(context, span);

				SpanLayer.asHttp(span);
				
				return true;
			} else {
				return false;
			}
		} catch (Exception t) {
			logger.error("Error :", t);
			return false;
		}
	}

	public static String getNativeUrlFromContext(RestMessageContext context) {
		
		return context.getTo().getAddress();
	}
	
	/**
	 * 
	 * @return
	 */
	public static boolean isActiveSpanValid() {
	
		return !(ContextManager.activeSpan() instanceof NoopSpan);
	}
	
	/**
	 * Populates all data in given span from http object.
	 * 
	 * @param gState - current HTTPState
	 * @param span   - current active span
	 */
	public static void populateSpanData(RestMessageContext context, AbstractSpan span) {
		// decorate the span with tags
		
		Tags.UHM.PARENT_LANDSCAPE.set(span, "webMethods");
		
		Tags.UHM.OPERATION_NAME.set(span, getOperationName(context));
		Tags.URL.set(span, UhmUtils.sanitizeFullyQualifiedName(context.getResourcePath()));
		Tags.HTTP.METHOD.set(span, context.getMethodType());
		Tags.UHM.FULLY_QUALIFIED_NAME.set(span, getOperationName(context));
		//Tags.UHM.STAGE.set(span, context.getInvokeState().getStageID());
		Tags.UHM.TENANT_ID.set(span, getTenantID(context));
		if (Config.Agent.SERVICE_NAME != null) {
			Tags.UHM.COMPONENT.set(span, Config.Agent.SERVICE_NAME.trim());
		} else {
			Tags.UHM.COMPONENT.set(span, "API Gateway");
		}
	}

	public static ContextCarrier createContextCarrier(RestMessageContext gState) throws ParseException {
		
		// get http headers from gState of httpDispatch
		
		Map<String, String> reqHeaders = gState.getRequestHeaders();
		
		String sw6Value = reqHeaders.get(SW6CarrierItem.HEADER_NAME) != null ? reqHeaders.get(SW6CarrierItem.HEADER_NAME) : getskywIdFromWMICHeaders(reqHeaders);
		
		logger.debug("sw6 value in req header " + sw6Value);

		ContextCarrier contextCarrier = new ContextCarrier();

		if (reqHeaders.containsKey("sw6")) {
			// create a context carrier with incoming request headers (we are looking for
			// sw6 header key)
			CarrierItem contextCarrierItem = contextCarrier.items();
			while (contextCarrierItem.hasNext()) {
				contextCarrierItem = contextCarrierItem.next();
				contextCarrierItem.setHeadValue(sw6Value);
			}
		}
		
		return contextCarrier;
	}
	
	public static String getskywIdFromWMICHeaders(Map<String, String> reqHeaders) throws ParseException {
		
		// check if value is present for the sw6 header or not
		// if not then check if it has X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON
		// header from WMIC-CTP
		
		String wmicExecutionParamHeader = reqHeaders.get(WMIC_EXECUTION_PARAM_HEADER_KEY);
		
		String sw6Value = null;
		
		if (!Strings.isNullOrEmpty(wmicExecutionParamHeader)) {
			JSONParser parser = new JSONParser();
			JSONObject json = (JSONObject) parser.parse(EncodeURL.decode(wmicExecutionParamHeader));
			sw6Value = (String) json.get(SW6CarrierItem.HEADER_NAME);
			logger.debug("sw6 value in wmic execution param header " + sw6Value);
		} else {
			logger.debug("sw6 value is null in wmic execution param header");
		}
		
		return sw6Value;
	}
	
	public static String getTenantID(RestMessageContext gState) {
		if (DEV_MODE) {
			// UHM local environments are setup as on-premise API Gateway, so we wont get
			// tenantID from it. this value is set in setenv.bat/sh file of IS
			return UHM_DEFAULT_TENANT_ID;
		} else {
			// If subdomain is available use it.
			String subdomain = gState.getRequestHeaders().get(X_WMIC_SUBDOMAIN_HEADER_KEY);
			logger.debug("Sub domain = " + subdomain);

			// use sub-domain if available, else use the tenantId (the no of wmic LJ)
			if (null != subdomain) {
				return subdomain;
			} else {
				if(logger.isDebugEnable()) {
					//log all http headers of IS to debug why sub-domain is not present in it
					Map<String, String> fieldsMap = gState.getRequestHeaders();
					logger.debug(fieldsMap.toString());
				}
				
				return InvokeState.getCurrentState().getTenantID();
			}
		}
	}

	public static boolean hasE2eTag(List<Tag> tags) {
	
		boolean found = false;
		
		for (Tag t : tags) {
			
			if (t.getName().equalsIgnoreCase(E2E_LABEL)) {
				found = true;
				break;
			}
		}
		
		return found;
	}
	
	public static String e2eTagForTransactionId(List<Tag> tags) {
		
		String found = null;
		
		for (Tag t : tags) {
			
			if (t.getName().startsWith("e2e:")) {
				found = t.getName().substring(4);
				break;
			}
		}
		
		return found;
	}
	
	public static String e2eTransactionIdForPathParameter(String tag, RestMessageContext messageContext) {
	
		String found = null;
		
		// TODO
		
		return found;
	}
	
	/**
	 * Helper method to get the operation name (api name) from the
	 * ServiceInvokeEvent
	 * 
	 * @param evt - the ServiceInvokeEvent from where we want to get the operation
	 *            name
	 * @return
	 */
	private static String getOperationName(RestMessageContext evt) {
		if (evt.getServiceName() != null) {
			return evt.getServiceName();
		} else {
			String addr = evt.getTo().getAddress();
			int loc = addr.indexOf("/gateway");
			
			if (loc != -1)
				addr = addr.substring(loc+9);
			
			loc = addr.indexOf("/");
			
			if (loc != -1) {
				addr = addr.substring(0, loc);
			}
			
			return addr;
		}
	}

	/**
	 * Helper method to get the fully qualified name (OperationName) from the
	 * ServiceInvokeEvent
	 * 
	 * @param evt - the ServiceInvokeEvent from where we want to get the fully
	 *            qualified name
	 * @return
	 */
	private static String getFQN(RestMessageContext evt) {
		return UhmUtils.sanitizeFullyQualifiedName(getOperationName(evt));
	}
}
