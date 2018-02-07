#!/bin/sh

cd `dirname $0`

AXIS_LIB="../../lib"
WSIF_LIB=../lib/wsif/lib
WSIF_BUILD_LIB=../lib/wsif/build/lib
LIB_JARS=`ls -1 $AXIS_LIB/*.jar`
WSIF_JARS=`ls -1 $WSIF_LIB/*.jar`
WSIF_BUILD_JARS=`ls -1 $WSIF_BUILD_LIB/*.jar`
LIST=""
for lib in $LIB_JARS $WSIF_JARS $WSIF_BUILD_JARS; do
    LIST=${LIST}:${1}${lib}
done

CLASSPATH=$LIST:$CLASSPATH

echo $CLASSPATH
