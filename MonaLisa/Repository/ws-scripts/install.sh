#!/bin/bash

java=`which java | grep -v -E -e "^which: no"`

while [ -z "${java}" ]; do
    echo -n "Path to java home directory: "
    read jt
    if [ -x "${jt}/bin/java" ]; then
	echo "The path you specified seems to be ok."
	java="${jt}/bin/java"
    else
	echo "No java executable was found in the path you specified. Please do not include /bin or /bin/java in the path."
    fi
done

java=`dirname "${java}"`
java=`dirname "${java}"`

cd `dirname $0`

DIR="`pwd`"

echo ${java} > conf/env.JAVA_HOME || exit

MWS=${MONALISA_WS}

while [ -z "${MWS}" ]; do
    echo -n "Please enter the MonALISA Web Services URL : "
    read MWS
done

echo -n  ${MWS} > conf/env.MONALISA_WS || exit

echo ""
echo ""
echo "Configuration completed successfully."
echo ""

#rm ${DIR}/`basename $0`
