#!/bin/bash

CP=.
for a in ../lib/*.jar ../../lib/*.jar ../../Clients/lib/*.jar ../../../WEBS/WEB_GRAPH/WEB-INF/lib/*.jar; do
    CP=${CP}:$a
done

CFG=""
if [ -z ${1} ]; then
    if [ -f ../../Clients/JStoreClient/conf/App.properties ]; then
	CFG=../../Clients/JStoreClient/conf/App.properties
    elif [ -f ../../conf/storage_configuration.properties ]; then
	CFG=../../conf/storage_configuration.properties
    fi
else
    CFG=${1}
fi

echo "CFG=${CFG}"

javac -classpath ${CP} ShowReceivedValues.java && java -classpath ${CP} ShowReceivedValues ${CFG}
