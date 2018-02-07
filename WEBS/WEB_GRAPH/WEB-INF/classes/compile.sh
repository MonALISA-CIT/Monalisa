#!/bin/bash

cd `dirname $0`

BASE=../../../../..

LIB=$BASE/lib

CP=.:$BASE/bin/alienpool:$BASE/lib/classes:$BASE/tomcat/lib/catalina.jar:$BASE/tomcat/lib/servlet-api.jar

for JAR in $LIB/*.jar; do
    CP="$CP:$JAR"
done

cat alimonitor/Page.java | sed "s#/home/monalisa/MLrepository/tomcat/webapps/ROOT#$(dirname $(dirname `pwd`))#g" > alimonitor/Page.java.new && mv alimonitor/Page.java.new alimonitor/Page.java

`cat ../../../../../conf/env.JAVA_HOME`/bin/javac -classpath "$CP" *.java */*.java
