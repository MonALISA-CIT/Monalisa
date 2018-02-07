#!/bin/bash

cd `dirname $0`

myreplace(){
    a=`echo $1 | sed 's/\//\\\\\//g'`
    b=`echo $2 | sed 's/\//\\\\\//g'`

    cat | sed "s/$a/$b/g"
}

(
export PGLIB=`pwd`/lib
export LD_LIBRARY_PATH=$PGLIB:$LD_LIBRARY_PATH
export PATH="`pwd`/bin:$PATH"

bin/initdb -D `pwd`/data -L `pwd`/share || exit

./start.sh

PORT=`cat pgsql.port`

sleep 2
bin/psql -U `id -u -n` -d template1 -h 127.0.0.1 -p $PORT -c "CREATE USER mon_user PASSWORD 'mon_pass' CREATEDB NOCREATEUSER;"
sleep 1
bin/psql -U mon_user -d template1 -h 127.0.0.1 -p $PORT -c "CREATE DATABASE mon_data;"
) &> init.log

echo "done"
