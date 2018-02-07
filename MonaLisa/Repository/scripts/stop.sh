#!/bin/bash

cd `dirname $0`

if [ "`id -u`" -eq 0 ]; then
    exec su - `cat conf/env.ACCOUNT` -c "`pwd`/`basename $0`"
fi

if [ ! "`id -u -n`" == "`cat conf/env.ACCOUNT`" ]; then
    echo ""
    echo "This is not the account from which the MonaLisa Repository was installed!"
    echo "Please su - `cat conf/env.ACCOUNT` before runing this script."
    echo ""
    exit 1
fi

scripts/testrun.sh

if [ $? -ne 0 ]; then
    echo "Another script is running, please wait for it to finish"
    exit
fi

export JAVA_HOME=`cat conf/env.JAVA_HOME`
export PATH=$JAVA_HOME/bin:$PATH:/sbin:/usr/sbin:/usr/local/sbin

scripts/stop_jstoreclient.sh
scripts/stop_pgsql.sh

echo -n "Removing crontab entry ... "

CRON=`crontab -l | grep -v -E -e "(scripts/verify.sh)|(^#.+(DO NOT)|(/tmp/crontab)|Cron version)"`
echo "$CRON" | crontab -

echo "done"

if [ -z "$LOCK_AQUIRED" ]; then
    rm scripts/TESTRUN
fi
