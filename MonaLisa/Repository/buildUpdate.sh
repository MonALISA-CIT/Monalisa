#!/bin/bash

#- tomcat curat
#- axis curat
#- mysql-store
#- webapps
#- jstoreclient
#- libs (mysql, ml, axis)
#- deploy

# current configuration. change this to your liking
MONALISA_DIST_DIR="../"
TOMCAT="tomcat"
AXIS="axis-1_1"
DEST="MLrepository-upgrade"
###################################################

#make sure no leftovers are here
rm -rf ${DEST} ${DEST}.tgz

#init
mkdir ${DEST}
mkdir ${DEST}/lib
mkdir ${DEST}/conf

#copy the files
cp -r bin ${DEST}/bin

#- tomcat
cp -r ${TOMCAT} ${DEST}/tomcat
rm -rf ${DEST}/tomcat/shared/lib
ln -s ../../lib ${DEST}/tomcat/shared/lib

cp -r ../../WEBS/WEB_GRAPH ${DEST}/tomcat/webapps/ROOT
cp -r ../../WEBS/WEB_WAP ${DEST}/tomcat/webapps/wap
cp -r ../../WEBS/axis ${DEST}/tomcat/webapps/axis

rm -rf ${DEST}/tomcat/webapps/wap/WEB-INF/conf
ln -s ../../ROOT/WEB-INF/conf ${DEST}/tomcat/webapps/wap/WEB-INF/conf

mv ${DEST}/tomcat/webapps/ROOT/WEB-INF/lib/*.jar ${DEST}/lib
mv ${DEST}/tomcat/webapps/axis/WEB-INF/lib/*.jar ${DEST}/lib

#- axis
for jar in jaxrpc saaj axis commons-discovery commons-logging log4j-1.2.8 wsdl4j; do
    cp ${AXIS}/lib/${jar}.jar ${DEST}/lib
done

#- JStoreClient

for jar in jini-core jini-ext tools mysql-driver JStoreClient; do
    cp ${MONALISA_DIST_DIR}/Clients/lib/${jar}.jar ${DEST}/lib
done

find ${DEST} -type d -name CVS -exec rm -rf \{\} \; &>/dev/null

#remove possibly customized files/dirs
rm -rf \
    ${DEST}/conf \
    ${DEST}/tomcat/bin \
    ${DEST}/tomcat/conf \
    ${DEST}/tomcat/shared \
    ${DEST}/tomcat/webapps/ROOT/WEB-INF/conf \
    ${DEST}/tomcat/webapps/ROOT/*.*

#make the tarball
tar -czf ${DEST}.tgz ${DEST}

#cleanup
rm -rf ${DEST}
