#!/bin/bash

CP='.'

#for a in lib/*.jar ~/MLrepository/tomcat/shared/lib/*.jar ~/MLrepository/tomcat/common/lib/*.jar ~/MLrepository/tomcat/server/lib/*.jar; do
for a in lib/*.jar ~/MLrepository/tomcat/shared/lib/*.jar ~/MLrepository/tomcat/common/lib/*.jar; do
    echo $a
    CP=${CP}:$a
done

java -classpath ${CP} org.apache.axis.client.AdminClient undeploy.wsdd
java -classpath ${CP} org.apache.axis.client.AdminClient deploy.wsdd

