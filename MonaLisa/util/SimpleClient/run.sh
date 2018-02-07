#!/bin/bash

CP=.
for a in ../lib/*.jar ../../lib/*.jar ../../Clients/lib/*.jar ../../../WEBS/WEB_GRAPH/WEB-INF/lib/*.jar; do
    CP=${CP}:$a
done

javac -classpath ${CP} FindMLServices.java && java -classpath ${CP} -Djava.security.policy="policy.all" FindMLServices
