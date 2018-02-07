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
AXIS="axis"
DEST="MLrepository"
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
#ln -s ../../lib ${DEST}/tomcat/shared/lib

mkdir -p ${DEST}/tomcat/webapps

cp -r ../../WEBS/WEB_GRAPH ${DEST}/tomcat/webapps/ROOT
cp -r ../../WEBS/WEB_WAP ${DEST}/tomcat/webapps/wap
cp -r ../../WEBS/axis ${DEST}/tomcat/webapps/axis

rm -rf ${DEST}/tomcat/webapps/wap/WEB-INF/conf
ln -s ../../ROOT/WEB-INF/conf ${DEST}/tomcat/webapps/wap/WEB-INF/conf

mv ${DEST}/tomcat/webapps/ROOT/WEB-INF/lib/*.jar ${DEST}/lib
cp ../Service/lib/backport-util-concurrent.jar ${DEST}/lib
cp ../../lib/jep-2.4.0.jar ${DEST}/lib
cp ../../lib/dnsjava-1.3.2.jar ${DEST}/lib
cp ../../lib/ymsg.jar ${DEST}/lib
#mv ${DEST}/tomcat/webapps/wap/WEB-INF/lib/*.jar ${DEST}/lib
mv ${DEST}/tomcat/webapps/axis/WEB-INF/lib/*.jar ${DEST}/lib
cp -r lib/* ${DEST}/lib

rm ${DEST}/lib/gnujaxp.jar

#- axis
for jar in jaxrpc saaj axis commons-discovery commons-logging log4j-1.2.8 wsdl4j; do
    cp ${AXIS}/${jar}.jar ${DEST}/lib
done

#- util
#cp -r ../util ${DEST}

#- JStoreClient
cp -r ../Clients/JStoreClient ${DEST}/JStoreClient
rm -f ${DEST}/JStoreClient/log.out

#- Embedded JStoreClient client
cp -r ../Clients/Embedded ${DEST}/Embedded
cp -r ../Clients/Scheduler ${DEST}/Scheduler
rm -f ${DEST}/Embedded/log.out ${DEST}/Embedded/*.class
cp -r ../Clients/DirectClient ${DEST}/DirectClient


for jar in jini-core jini-ext tools mysql-driver JStoreClient image-grid3 postgresql; do
    cp ${MONALISA_DIST_DIR}/Clients/lib/${jar}.jar ${DEST}/lib
done

cp -r conf ${DEST}/JStoreClient

#- mysql_store
#cp -r mysql_store ${DEST}
cp -r pgsql_store ${DEST}

#- scripts
cp -r scripts/* ${DEST}
rm ${DEST}/scripts/*mysql*
chmod a+x ${DEST}/install.sh

cp ws-scripts/MWS.sh ${DEST}/conf

#- WS clients
cp -r ../Clients/WS ${DEST}/WS-Clients

#create links to the config files
ln -s ../JStoreClient/conf/App.properties ${DEST}/conf/storage_configuration.properties
#ln -s ../mysql_store/Start_Mysql.sh ${DEST}/conf/mysql_start_script.sh
ln -s ../pgsql_store/data/postgresql.conf ${DEST}/conf/postgresql.conf
ln -s ../tomcat/conf/server.xml ${DEST}/conf/web_server_config.xml
ln -s ../tomcat/webapps/ROOT/WEB-INF/conf ${DEST}/conf/website_config_files
#rm -rf ${DEST}/mysql_store/current ${DEST}/mysql_store/v4.0.15 ${DEST}/mysql_store/v4.0.18
#ln -sf latest ${DEST}/mysql_store/current

find ${DEST} -type d -name CVS -exec rm -rf \{\} \; &>/dev/null
find ${DEST} -type d -name .svn -exec rm -rf \{\} \; &>/dev/null

chmod a-x ${DEST}/lib/*.jar

#put some basic maps
#tar -xzf maps.tgz -C ${DEST}/tomcat/webapps/ROOT/WEB-INF/classes

#make the tarballs
tar -cjf ${DEST}.tar.bz2 ${DEST}
tar -czf ${DEST}.tgz ${DEST}

#cleanup
rm -rf ${DEST}

#update the latest mysql
#cd `dirname $0`/mysql_store
#tar -czf /opt/tomcat/webapps/ROOT/repupdate/mysql-latest.tar.gz latest
