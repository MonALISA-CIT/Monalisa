#!/bin/bash

cd `dirname $0`

if [ -f pgsql_store.tar.gz ]; then
    tar -xzf pgsql_store.tar.gz && rm pgsql_store.tar.gz
fi

echo -n "Initializing PostgreSQL database ... "

myreplace(){
    a=`echo $1 | sed 's/\//\\\\\//g'`
    b=`echo $2 | sed 's/\//\\\\\//g'`

    cat | sed "s/$a/$b/g"
}

USERNAME=`id -u -n`
PWD=`pwd`

(
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:./lib"
export PGLIB="$PWD/lib"
export PATH="$PWD/bin:$PATH"

if [ -f share/conversion_create.sql.template ]; then
    cat share/conversion_create.sql.template | myreplace "\$libdir" "`pwd`/lib" > share/conversion_create.sql
fi

chmod 700 "data" &>/dev/null

bin/initdb -D "$PWD/data" -L "$PWD/share" -E UNICODE --no-locale

cp defaults/* data/

chmod 700 "data" &>/dev/null

PORT=`cat data/postgresql.conf | grep -E -e "^port" | cut -d= -f2`

../scripts/start_pgsql.sh

sleep 5

PSQL="bin/psql  -h 127.0.0.1 -p $PORT"

$PSQL -U $USERNAME -d template1 -c "CREATE USER mon_user PASSWORD 'mon_pass' CREATEDB NOCREATEUSER;"
sleep 1
$PSQL -U mon_user  -d template1 -c "CREATE DATABASE mon_data;"
sleep 1
$PSQL -U $USERNAME -d mon_data  -c \
    "CREATE FUNCTION plpgsql_call_handler() RETURNS language_handler AS '`pwd`/lib/plpgsql.so' LANGUAGE 'C';	\
    CREATE TRUSTED LANGUAGE 'plpgsql' HANDLER plpgsql_call_handler LANCOMPILER 'PL/pgSQL';	\
    GRANT USAGE ON LANGUAGE plpgsql TO mon_user;"

) &> init.log

echo "done"
