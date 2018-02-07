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
    echo "  Another script is running, please wait for it to finish"
    echo ""
    echo "  You can manually rm scripts/TESTRUN and start the repository"
    echo "but you shouldn't do this unless you are sure that it is not already running"
    echo ""
    exit
fi

export JAVA_HOME=`cat conf/env.JAVA_HOME`
export PATH=$JAVA_HOME/bin:$PATH:/sbin:/usr/sbin:/usr/local/sbin

scripts/start_pgsql.sh
scripts/start_jstoreclient.sh

echo -n "Adding crontab entry ... "

CRON=`crontab -l | grep -v -E -e "(scripts/verify.sh)|(^#.+(DO NOT)|(/tmp/crontab)|Cron version)"`
(echo "$CRON"; echo "* * * * * `pwd`/scripts/verify.sh >> `pwd`/scripts/verify.log 2>&1") | crontab -

echo "done"

if [ -z "$LOCK_AQUIRED" ]; then
    rm scripts/TESTRUN
fi
