package com.softwareag.uhm.api.instrumentation;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import com.softwareag.apigateway.core.init.TenantInfoProvider;
import com.softwareag.pg.config.ConfigProvider;
import com.softwareag.pg.utils.GatewayConstants;
import com.softwareag.pg.utils.Utils;

/**
 * Utils class for APIGW containing the utility methods
 * 
 * @author rika
 *
 */
public class APIGWUtils {
	private static final ILog logger = LogManager.getLogger(APIGWUtils.class);
	private static boolean DEV_MODE = Boolean.valueOf(System.getProperty("UHM_DEV_MODE", "false"));
	private static String UHM_DEFAULT_TENANT_ID = System.getProperty("UHM_DEFAULT_TENANT_ID", "");

	/**
	 * Get the TenantID from the cloudConfig property (for WMIC deployed tenants) or
	 * from the tenantInfoProvider bean (for sagcloud tenants)
	 * 
	 * @return
	 */
	public static String getTenantID() {
		// commented the below as it returns the tenant name
		if (DEV_MODE) {
			// UHM local environments are setup as on-premise API Gateway, so we wont get
			// tenantID from it. this value is set in setenv.bat/sh file of IS
			return UHM_DEFAULT_TENANT_ID;
		} else {
			// first we try to get the subdomain of the tenant
			String tenantId = ((TenantInfoProvider) ConfigProvider.getInstance().getBeanProvider()
					.getBean("tenantInfoProvider")).getTenantId();
			if (null != tenantId) {
				return tenantId;
			}

			// if we dont get subdomain then we use the tenantId of wmic lj
			Object cloudTenantID = Utils.getConfigurationPropertyValue(GatewayConstants.CLOUD_CONFIG,
					GatewayConstants.CLOUD_TENANT_ID);
			if (cloudTenantID != null) {
				return (String) cloudTenantID;
			}

		}
		logger.error("TenantID is not available; this transaction will not show in the UI.");
		return null;
	}
}
