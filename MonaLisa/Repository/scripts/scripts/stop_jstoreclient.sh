#!/bin/bash

cd `dirname $0`

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin:`cat ../conf/env.REPOSITORY_DIR`/bin"

if [ "`id -u`" -eq 0 ]; then
    exec su - `cat ../conf/env.ACCOUNT` -c "`pwd`/`basename $0`"
fi

echo -n "Stopping store client and web server ... "

pgrep -f ../lib/JStoreClient.jar | xargs kill &>/dev/null
sleep 5
pgrep -f ../lib/JStoreClient.jar | xargs kill -9 &>/dev/null

echo "done"
