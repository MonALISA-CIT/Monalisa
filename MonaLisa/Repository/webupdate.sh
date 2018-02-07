#!/bin/bash

cd `dirname $0`

DIR=/home/repupdate/public_html

cp -f ../../WEBS/WEB_GRAPH/WEB-INF/lib/*.jar $DIR
cp -f ../../WEBS/axis/WEB-INF/lib/*.jar $DIR

for jar in jini-core jini-ext tools mysql-driver postgresql JStoreClient; do
    cp -f ../Clients/lib/${jar}.jar $DIR
done

for jar in backport-util-concurrent.jar jep-2.4.0.jar dnsjava-1.3.2.jar ymsg.jar smack.jar smackx.jar cryptix.jar jce-jdk13.jar jgsi.jar puretls.jar ; do
    cp -f ../../lib/$jar $DIR
done

cp -f axis/*.jar $DIR

cp -f ../../WEBS/axis/WEB-INF/*.wsdd $DIR

cp scripts/scripts/backup.sh $DIR

cp ../Clients/JStoreClient/njGlobal $DIR
chmod a-x $DIR/njGlobal

./mysql_store/latest/bin/mysql --version | cut "-d " -f 6 > $DIR/mysql.version

tar --exclude-from exclude-tomcat.lst -czf $DIR/tomcat.tgz tomcat

jar xf tomcat/lib/catalina.jar org/apache/catalina/util/ServerInfo.properties
TOMCATVER=`cat org/apache/catalina/util/ServerInfo.properties | grep "server.info=" | cut -d/ -f2`
rm -rf org
echo ${TOMCATVER} > $DIR/tomcat.version

jar xf axis/axis.jar META-INF/MANIFEST.MF
AXISVER=`cat META-INF/MANIFEST.MF | grep "Implementation-Version:" | cut -d: -f2-`
rm -rf META-INF
echo ${AXISVER} > $DIR/axis.version

cp $DIR/WEBS/WEB_GRAPH/info.jsp $DIR/WEBS/WEB_GRAPH/info.jspt
cp $DIR/WEBS/WEB_GRAPH/admin.jsp $DIR/WEBS/WEB_GRAPH/admin.jspt
cp $DIR/WEBS/WEB_GRAPH/cache.jsp $DIR/WEBS/WEB_GRAPH/cache.jspt

cp scripts/ChangeLog $DIR

cp -r scripts $DIR
