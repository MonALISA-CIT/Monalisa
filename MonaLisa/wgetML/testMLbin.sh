#!/bin/sh

PRG_DIR=`dirname "$0"`
MonaLisa_HOME=`dirname "$0"`"/.."

LIB_DIR=${MonaLisa_HOME}/Service/lib

LOG_FILE=${PRG_DIR}/"testMLbin.log"

for JAR_FILE in ${LIB_DIR}/*; do
    echo `zip -T $JAR_FILE`
done

exit 0
