#!/bin/bash

#- tomcat curat
#- axis curat
#- mysql-store
#- webapps
#- jstoreclient
#- libs (mysql, ml, axis)
#- deploy

# current configuration. change this to your liking
TOMCAT="tomcat"
AXIS="axis-1_1"
DEST="monalisaWSClients"
###################################################

#make sure no leftovers are here
rm -rf ${DEST} ${DEST}.tgz

#init
mkdir ${DEST}
mkdir ${DEST}/lib
mkdir ${DEST}/conf

#- axis
for jar in jaxrpc saaj axis commons-discovery commons-logging log4j-1.2.8 wsdl4j; do
    cp ${AXIS}/lib/${jar}.jar ${DEST}/lib
done

#- scripts
cp -r ws-scripts/* ${DEST}
chmod a+x ${DEST}/install.sh
mv ${DEST}/MWS.sh ${DEST}/conf/MWS.sh

#- WS clients
cp -r ../Clients/WS ${DEST}/WS-Clients

#make the tarball
tar -czf ${DEST}.tgz ${DEST}

#cleanup
rm -rf ${DEST}
