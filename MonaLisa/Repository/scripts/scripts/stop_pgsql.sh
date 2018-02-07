#!/bin/bash

cd `dirname $0`/../pgsql_store

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

echo -n "Stopping postgresql ... "

PORT=`cat data/postgresql.conf | grep -E -e "^port" | cut -d= -f2`

RUNNING=`fuser -n tcp $PORT|grep -v "^here"`
i=0

killall start_pgsql.sh &>/dev/null
	    
while [ ! -z "${RUNNING}" -a $i -lt 3 ]; do
    for signal in TERM TERM TERM TERM INT QUIT KILL; do
        fuser -k -${signal} -n tcp $PORT &>/dev/null
        sleep 1
        RUNNING=`fuser -n tcp $PORT|grep -v -E -e "^here"`
        if [ -z "$RUNNING" ]; then
#	    killall pg_autovacuum &>/dev/null
	    echo "done"
	    
	    exit 0
	fi
    done
	
    i=$((i+1))
    RUNNING=`fuser -n tcp $PORT|grep -v -E -e "^here"`
done

if [ ! -z "$RUNNING" ]; then
#    killall pg_autovacuum &>/dev/null
    echo "done"
    exit 1
fi

echo "done"
