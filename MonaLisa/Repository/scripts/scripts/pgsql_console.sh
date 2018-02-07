#!/bin/bash

cd `dirname $0`/../pgsql_store

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

PORT=`cat data/postgresql.conf | grep -E -e "^port" | cut -d= -f2`

export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:./lib
export PGLIB=`pwd`/lib
export JAVA_MEM=256

bin/psql -U mon_user -h 127.0.0.1 -p $PORT -d mon_data "$@"
