#!/bin/bash

cd `dirname $0`

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

if [ "`id -u`" -eq 0 ]; then
    exec su - `cat ../conf/env.ACCOUNT` -c "`pwd`/`basename $0`"
fi

(cd ../mysql_store && ./Stop_Mysql.sh)
