#!/bin/bash

cd `dirname $0`

export JAVA_HOME=/usr

#ant clean
#ant dist_ser
#ant dist_client
ant dist_web && ./updateLocal.sh
# && ./update.sh
