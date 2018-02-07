#!/bin/bash

export JAVA_HOME=`cat ../conf/env.JAVA_HOME`
export PATH=$JAVA_HOME/bin:$PATH

CP="."

for a in ../lib/*.jar; do
    CP="$CP:$a"
done

javac -classpath ${CP} *.java

