#!/bin/bash

cd `dirname $0`

MAIN="lia.util.security.authz.AuthZService"

if [ ! -z "$1" ]; then
    MAIN="$1"
fi

for jar in lib/*.jar ; do
    CP="$CP:$jar"
done

java -XX:CompileThreshold=20 -server \
    -Dlia.Monitor.ConfigURL=file:conf/authz.properties \
    -Djava.util.logging.config.file=conf/logger.config \
    -Djava.security.policy=conf/java.policy \
    -Dlia.Monitor.ConfigURL=file:conf/authz.properties \
    -classpath "$CP"\
    $MAIN &>authz.log &

