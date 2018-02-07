#!/bin/bash

cd `dirname $0`

export LD_LIBRARY_PATH=`pwd`/lib:$LD_LIBRARY_PATH
export PGLIB=`pwd`/lib
export PATH="`pwd`/bin:$PATH"

psql -U `id -u -n` -h 127.0.0.1 -p `cat pgsql.port` mon_data "$@"
