#!/bin/bash

cd `dirname $0`

LOCALPORTS=`netstat -ltn | grep -E -e "^tcp" | awk '{print $4}' | cut -d: -f2`
PORT=5433
OK=0
while [ "${OK}" -eq 0 ]; do
    OK=1
    PORT=$((PORT+1))
    for port in $LOCALPORTS; do
        if [ "${PORT}" -eq "${port}" ]; then
	    OK=0
	    break;
	fi
    done
done
							
echo $PORT > pgsql.port

export PGLIB=`pwd`/lib
export LD_LIBRARY_PATH=$PGLIB:$LD_LIBRARY_PATH
export PATH="`pwd`/bin:$PATH"

bin/postmaster -D data -F -i -h 127.0.0.1 -p $PORT -k `pwd` &>postmaster.log &

sleep 5
