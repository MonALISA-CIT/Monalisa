#!/bin/bash

cd `dirname $0`

./testrun.sh || exit

export LOCK_AQUIRED=true

UNAME=`id -u -n`

PSTREE=`pstree $UNAME 2>&1`

ERR=`echo "$PSTREE" | grep -E -e "^/proc"`

if [ ! -z "$ERR" ]; then
    echo "Error : $ERR"
    echo "$PSTREE"
    exit
fi

#older systems show all java processes
#JAVAS=`echo "$PSTREE" | grep -E -e '^.*java---java-(-|\+)-[0-9]{2,3}\*\[java\]'`

#newer systems show a single java process
JAVAS=`echo "$PSTREE" | grep -E -e '^.*java'`

if [ -z "$JAVAS" ]; then
        echo "`date` : Repository is not running"
        echo "$PSTREE"
        ../stop.sh
        ../start.sh
        echo "----------------------"
        echo ""
fi

rm TESTRUN &>/dev/null
