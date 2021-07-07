## e2e Agent for API Gateway

Example project to showcase e2e tracing from API Gateway. 


# Setup

Install the WmE2eAPIAgent from ./build/source/packages to the packages directory of your integration Server that includes the API Gateway.

Add the following lines to your <SAG>/profiles/IS_Default/configuration/custom_wrapper.conf

```

wrapper.java.additional.505=-Xbootclasspath/a:/opt/softwareag/IntegrationServer/instances/default/packages/WmE2eAPIAgent/resources/uhm-apm-agent.jar:"%JAVA_BOOT_CLASSPATH%"
wrapper.java.additional.510=-javaagent:/opt/softwareag/IntegrationServer/instances/default/packages/WmE2eAPIAgent/resources/uhm-apm-agent.jar

```


Make sure to update the path to reflect your installation.

You will need to move a jar file so that agent can access the API Gateway objects.

```

$ cd /opt/softwareag/IntegrationServer/instances/default/packages/WmAPIGateway/code/jars
$ mv apigateway-runtime-base.jar static

```


Then restart your server.

# Usage

Add a tag "e2e" to your API 


