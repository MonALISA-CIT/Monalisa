#!/bin/bash

cd `dirname $0`

MLREP=`cat ../conf/env.REPOSITORY_DIR`

#echo $MLREP

if [ ! -z "$LOCK_AQUIRED" ]; then
#    echo "the lock is in env, good"
    exit 0
fi

if [ -f TESTRUN ]; then
#    echo "TESTRUN already exists"

    pid=`cat TESTRUN`
    
    cd -P /proc/$pid/cwd 2>&1
    
    if [ $? -eq 0 ]; then
#	echo "The process is valid: `pwd`"
	
	if [ ! -z `pwd | grep "$MLREP"` ]; then 
#	    echo "It's one of my scripts, not good"
	    exit 1; 
	fi
    fi

    if [ ! -z `ls -l --color=none /proc/$pid/cwd | cut -d\> -f2- | grep "$MLREP"` ]; then
#        echo "ls said it's mine, quiting"
        exit 1;
    fi
fi

#echo "Putting my ppid there"
echo "$PPID" > TESTRUN
exit 0
