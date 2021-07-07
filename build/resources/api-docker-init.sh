#!/bin/sh
#
# startup script
#


# Define SIGTERM-handler for graceful shutdown
term_handler() {
  if [ $childPID -ne 0 ]; then
    /bin/bash ./docker-stop.sh
  fi
  exit 143; # 128 + 15 -- SIGTERM
}
# setup SIGTERM Handler
trap 'kill ${!}; term_handler' SIGTERM

# start up components

echo "Starting webMethods components......"

#/opt/sag/profiles/SPM/bin/startup.sh

USER=`id -u -n`

if [ $USER == 'sagadmin' ]
then
	/opt/softwareag/profiles/IS_default/bin/startDebugMode.sh
else
	su sagadmin -c '/opt/softwareag/profiles/IS_default/bin/startDebugMode.sh'
fi

# keep container running

sleep 10

echo "outputing server.log to stdout...."

ALERT_LOG=/opt/softwareag/IntegrationServer/instances/default/logs/server.log
tail -f $ALERT_LOG &
childPID=$!
wait $childPID

# end
