#!/bin/bash

export JAVA_HOME=`cat ../conf/env.JAVA_HOME`
export PATH=$JAVA_HOME/bin:$PATH

CP="."

for a in ../lib/*.jar; do
    CP="$CP:$a"
done

java \
    -server -Xmx256m -XX:CompileThreshold=500 \
    -classpath ${CP} \
    -Djava.security.policy=policy.all \
    -Dlia.Monitor.ConfigURL=file:conf/App.properties \
    -Djava.util.logging.config.class=lia.Monitor.monitor.LoggerConfigClass \
    DirectClient
