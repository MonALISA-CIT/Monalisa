#!/bin/bash

cd `dirname $0`/../pgsql_store

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

PORT=`cat data/postgresql.conf | grep -E -e "^port" | cut -d= -f2`

export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:./lib
export PGLIB=`pwd`/lib

echo -n "Starting postgresql ... "

ME=`id -u -n`

test=`fuser -n tcp $PORT|grep -v "^here" | grep -v "^address ::"`

if [ -z "$test" ]; then
    test=`netstat -ltpn 2>/dev/null | grep -E -e "^tcp\\W+[0-9]+\\W+[0-9]+\\W+.+:$PORT\\W"`
fi

if [ ! -z "$test" ]; then
    echo "already running: $test"
    exit 1
fi

(
    bin/postmaster -D data -F -i &>postmaster.log &
#    sleep 60
#    bin/pg_autovacuum -D -U $ME -H 127.0.0.1 -p $PORT &>autovacuum.log
) &

echo "done"
