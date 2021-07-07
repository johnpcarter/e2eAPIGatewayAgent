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

import static java.util.Objects.nonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW6CarrierItem;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.uhm.UhmUtils;
import org.apache.skywalking.apm.agent.core.utils.CollectionUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.apache.skywalking.apm.dependencies.com.google.common.base.Strings;
import com.wm.net.EncodeURL;

/**
 * Utils class for IS containing the utility methods
 * 
 * @author rika
 *
 */
public final class ISHttpHandlerUtils {
	
	private ISHttpHandlerUtils() {
		// do nothing
	}

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
					&& !ISHttpHandlerUtils.isTransactionIDAvailable(ContextManager.activeSpan())) {
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
	public static void startEntrySpan(HTTPState gState) {
		try {
			ContextCarrier contextCarrier = createContextCarrier(gState);

			// start a entry span to denote that we have entered into IS
			String opName = getOpenrationName(gState);
			AbstractSpan span = ContextManager.createEntrySpan(opName, contextCarrier);
			// only if a valid span is created, we should populate the rest of span
			if (ContextManager.isActive()) {// SpanValid()) {
				logger.debug("Created the Entry span..." + span.getOperationName());

				populateSpanData(gState, span);

				SpanLayer.asHttp(span);
			}
		} catch (Exception t) {
			logger.error("Error :", t);
		}
	}

	/**
	 * Populates all data in given span from http object.
	 * 
	 * @param gState - current HTTPState
	 * @param span   - current active span
	 */
	public static void populateSpanData(HTTPState gState, AbstractSpan span) {
		// decorate the span with tags
		Tags.UHM.OPERATION_NAME.set(span, getOpenrationName(gState));
		Tags.URL.set(span, UhmUtils.sanitizeFullyQualifiedName(gState.getRequestUrl()));
		Tags.HTTP.METHOD.set(span, gState.getRequestTypeAsString());
		Tags.UHM.FULLY_QUALIFIED_NAME.set(span, getOpenrationName(gState));
		Tags.UHM.STAGE.set(span, gState.getInvokeState().getStageID());
		Tags.UHM.TENANT_ID.set(span, getTenantID(gState));
		if (Config.Agent.SERVICE_NAME != null) {
			Tags.UHM.COMPONENT.set(span, Config.Agent.SERVICE_NAME.trim());
		} else {
			Tags.UHM.COMPONENT.set(span, "");
		}
	}

	/**
	 * Creates contextCarrier with given http request.
	 * 
	 * @param gState
	 * @return
	 * @throws ParseException
	 */
	public static ContextCarrier createContextCarrier(HTTPState gState) throws ParseException {
		// get http headers from gState of httpDispatch
		Map<String, String> reqHeaders = gState.getReqHdr().getFieldsMap();
		String sw6Value = reqHeaders.get(SW6CarrierItem.HEADER_NAME);
		logger.debug("sw6 value in req header " + sw6Value);
		// check if value is present for the sw6 header or not
		// if not then check if it has X-WMIC-EXECUTION-CONTROL-PARAMETERS-AS-JSON
		// header from WMIC-CTP
		String wmicExecutionParamHeader = reqHeaders.get(WMIC_EXECUTION_PARAM_HEADER_KEY);
		if (!Strings.isNullOrEmpty(wmicExecutionParamHeader)) {
			JSONParser parser = new JSONParser();
			JSONObject json = (JSONObject) parser.parse(EncodeURL.decode(wmicExecutionParamHeader));
			sw6Value = (String) json.get(SW6CarrierItem.HEADER_NAME);
			logger.debug("sw6 value in wmic execution param header " + sw6Value);
		} else {
			logger.debug("sw6 value is null in wmic execution param header ");
		}

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

	/**
	 * Get the operation name from the RequestUrl of the gState
	 * 
	 * @param gState - current HTTPState
	 * @return
	 */
	public static String getOpenrationName(HTTPState gState) {
		return UhmUtils.sanitizeFullyQualifiedName(gState.getRequestUrl());
	}

	/**
	 * Get the tenantID from the invokeState of the gState
	 * 
	 * @param gState - current HTTPState
	 * @return
	 */
	public static String getTenantID(HTTPState gState) {
		if (DEV_MODE) {
			// UHM local environments are setup as on-premise API Gateway, so we wont get
			// tenantID from it. this value is set in setenv.bat/sh file of IS
			return UHM_DEFAULT_TENANT_ID;
		} else {
			// If subdomain is available use it.
			String subdomain = gState.getReqHdr().getFieldsMap().get(X_WMIC_SUBDOMAIN_HEADER_KEY);
			logger.debug("Sub domain = " + subdomain);

			// use sub-domain if available, else use the tenantId (the no of wmic LJ)
			if (null != subdomain) {
				return subdomain;
			} else {
				if(logger.isDebugEnable()) {
					//log all http headers of IS to debug why sub-domain is not present in it
					Map<String, String> fieldsMap = gState.getReqHdr().getFieldsMap();
					logger.debug(fieldsMap.toString());
				}
				
				return gState.getInvokeState().getTenantID();
			}
		}
	}

}
