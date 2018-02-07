#!/bin/bash

cd `dirname $0`

export PATH=.:$PATH:/sbin:/usr/sbin:/usr/local/sbin

rm data/postmaster.pid &>/dev/null

if [ -f "pgsql.port" ]; then
    PORT=`cat pgsql.port`
    RUNNING=`fuser -n tcp $PORT 2>&1|grep -v "^here"`
    i=0

    if [ -z "${RUNNING}" ]; then
        exit 0
    fi

    echo -n "Trying to stop epgsqldb..."
	    
    while [ ! -z "${RUNNING}" -a $i -lt 3 ]; do
        for signal in TERM INT QUIT KILL; do
    	    fuser -k -${signal} -n tcp $PORT &>/dev/null
    	    sleep 1
            RUNNING=`fuser -n tcp $PORT 2>&1|grep -v "^here"`
	    if [ -z "$RUNNING" ]; then
		rm -f pgsql.port
		echo "OK"
	        exit 0
	    fi
	done
	
	i=$((i+1))
	RUNNING=`fuser -n tcp $PORT 2>&1|grep -v "^here"`
    done

    if [ ! -z "$RUNNING" ]; then
        echo "Not OK"
        exit 1
    else
        rm -f pgsql.port
        echo "OK"
        exit 0
    fi

else
    echo "pgsql.port is missing! Maybe epgsqldb already stoped or not running!"
    exit 1
fi
