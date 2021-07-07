#
# Template to customise msc based image
#

FROM wm-api-gateway:10.7
LABEL MAINTAINER="johncarter@softwareag.com" \
	DESCRIPTION="API Gateway with e2e agent package" \
	CUSTOM="true" \
	SAG="true" \
	BUILD=1 \
	TYPE="API Gateway" 

# define exposed ports
	
EXPOSE 5555	
EXPOSE 9999	

# user to be used when running scripts
USER sagadmin

# files to be added to based image (includes configuration and package)
	
	RUN mkdir -p /opt/softwareag/EventDataStore/data
	RUN mkdir -p /opt/softwareag/InternalDataStore/data
	RUN mkdir -p /opt/softwareag/IntegrationServer/instances/default/cache
	RUN mkdir -p /opt/softwareag/IntegrationServer/instances/default/config
	RUN mkdir -p /opt/softwareag/IntegrationServer/instances/default/db
	
	ADD --chown=sagadmin ./resources/api-docker-init.sh /docker-init.sh		
	RUN chmod u+x /docker-init.sh		

	ADD --chown=sagadmin ./resources/skywalking_custom_wrapper.conf /opt/softwareag/profiles/IS_default/configuration/custom_wrapper.conf	
	ADD --chown=sagadmin ./source/packages/WmE2eAPIAgent /opt/softwareag/IntegrationServer/instances/default/packages/WmE2eAPIAgent
	#ADD --chown=sagadmin ./source/packages/WmE2eAPIAgent/code/jars/static/*.jar /opt/softwareag/IntegrationServer/instances/default/packages/WmAPIGateway/code/jars/static
	
	RUN mv /opt/softwareag/IntegrationServer/instances/default/packages/WmAPIGateway/code/jars/apigateway-runtime-base.jar /opt/softwareag/IntegrationServer/instances/default/packages/WmAPIGateway/code/jars/static
	
	ENTRYPOINT /docker-init.sh	
			
			
			

