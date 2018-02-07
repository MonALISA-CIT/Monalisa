#!/bin/sh

cd `dirname $0`

AXIS_LIB="../../lib"
LIST=""
for jar in axis commons-logging commons-discovery wsdl4j jaxrpc saaj; do
    LIST=${LIST}:${1}${AXIS_LIB}/${jar}.jar
done

CLASSPATH=$LIST:$CLASSPATH

echo $CLASSPATH
