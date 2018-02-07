#!/bin/bash

cd `dirname $0`

export JAVA_HOME=/usr

ant clean dist_ser || exit 1

cp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar  /home/costing/pcalimonitor/tomcat/webapps/ROOT/download/
