#!/bin/bash

cd `dirname $0`

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

if [ "`id -u`" -eq 0 ]; then
    exec su - `cat ../conf/env.ACCOUNT` -c "`pwd`/`basename $0`"
fi    

export JAVA_HOME=`cat ../conf/env.JAVA_HOME`
export PATH=$JAVA_HOME/bin:$PATH
# export JAVA_MEM=4000
# export JAVA_OPTS="-XX:+DoEscapeAnalysis -XX:+UseCompressedOops -XX:+AggressiveOpts"

echo -n "Starting store client and web server ... "

(cd ../JStoreClient && ./njGlobal && echo "done") || echo "already running"
