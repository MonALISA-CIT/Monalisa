#!/bin/sh

cd ..

startdir=`pwd`

cd lib

echo -n "Backing up configuration ... "
../scripts/backup.sh &>/dev/null
echo "done"

#WEBREP=http://monalisa.cern.ch/~repupdate
WEBREP=http://192.91.244.18/~repupdate

mkdir old &>/dev/null

mv -f jcommon*.jar jfreechart*.jar old

rm ../tomcat/webapps/axis/WEB-INF/lib/ML_WS.jar ../tomcat/temp/* &>/dev/null

for a in JStoreClient ML_WS MSRC_WEB gnujaxp jfreechart-1.0.3 jcommon-1.0.6 jini-core jini-ext mysql-driver postgresql tools backport-util-concurrent jep-2.4.0 dnsjava-1.3.2 ymsg smack smackx cryptix jce-jdk13-131 jgsi puretls; do
    mv -f $a.jar old &>/dev/null
    echo -n "Downloading $a.jar ... "
    wget $WEBREP/$a.jar -O $a.jar -q
    echo "done"
done

echo ""

echo -n "Updating wsdd's ... "
for a in deploy undeploy server-config; do
    wget $WEBREP/$a.wsdd -O ../tomcat/webapps/axis/WEB-INF/$a.wsdd -q
done
echo "done"
echo ""

echo -n "Updating scripts ... "
for a in backup.sh; do
    wget $WEBREP/$a -O ../scripts/$a -q
    chmod a+x ../scripts/$a
done

wget $WEBREP/ChangeLog -O ../ChangeLog -q


echo -n "Fixing properties ..."
cd ../tomcat/webapps/ROOT/WEB-INF/conf
find . -name \*.properties | while read a; do
    X=`grep "combined" $a`
    
    if [ ! -z "$X" ]; then
        cat "$a" | sed 's/^descr=/charts.descr=/' > "$a.temp" &&  mv "$a.temp" "$a"
    fi
done
echo "done"
echo ""

cd ../classes
if [ ! -f SampleServlet.java ]; then
    echo -n "Downloading sample servlet ... "
    for a in SampleServlet.java SampleServlet.class compile.sh; do
	wget -q "$WEBREP/WEBS/WEB_GRAPH/WEB-INF/classes/$a" -O "$a"
    done
    chmod a+x compile.sh
    echo "done"
    echo ""
fi

cd ../..
mkdir overlib &>/dev/null
cd overlib
echo -n "Downloading Overlib ... "
wget -q "$WEBREP/overlib421.tgz" -O overlib.tgz
tar -xzf overlib.tgz
rm overlib.tgz
echo "done"

cd ../WEB-INF/res/display
#for res in hist_series.res rt_series.res hist_stat.res; do
#    wget -q "$WEBREP/res/display/$res" -O $res
#done

#for file in *.res; do
#    if [ ! -f "$file" ]; then
#	echo -n "New RES : $file ... "
#	wget -q "$WEBREP/res/display/$file" -O "$file"
#	echo "done"
#    fi
#done
#echo ""

#grep ":options:" hist.res &>/dev/null || (
#    echo -n "Upgrading hist.res ... "
#    wget -q "$WEBREP/res/display/hist.res" -O "hist.res"
#    echo "done"
#    echo ""
#)

#grep ":options:" pie.res &>/dev/null || (
#    echo -n "Upgrading pie.res ... "
#    wget -q "$WEBREP/res/display/pie.res" -O "pie.res"
#    echo "done"
#    echo ""
#)

#grep ":options:" rt.res &>/dev/null || (
#    echo -n "Upgrading rt.res ... "
#    wget -q "$WEBREP/res/display/rt.res" -O "rt.res"
#    echo "done"
#    echo ""
#)

#echo -n "Patching for imagemap ... "
#for file in hist.res rt.res pie.res; do
#    grep "usemap=" $file &> /dev/null || (
#	cat $file | sed 's/<img src=\"display?image=<<:image:>>\">/<<:map:>><img src=\"display?image=<<:image:>>\" usemap=\"#<<:image:>>\" border=0>/' > $file.new
#	mv $file.new $file
#    )
#done
#echo "done"
#echo ""

if [ ! -f "option.res" ]; then
    wget -q "$WEBREP/res/display/option.res" -O "option.res"
fi

cd ../masterpage
if [ ! -f "alternate.res" ]; then
    wget -q "$WEBREP/res/masterpage/alternate.res" -O "alternate.res"
fi

E=`cat masterpage.res | grep overlib.js`
if [ -z "$E" ]; then
    cat masterpage.res     | sed 's/<head>/<head>\n\t<script type=\"text\/javascript\" src=\"\/overlib\/overlib.js\"><\/script>/' > masterpage.res.new
    cat masterpage.res.new | sed 's/<body.*>/<body bgcolor=white>\n\t<div id="overDiv" style="position:absolute; visibility:hidden; z-index:1000;"><\/div>/' > masterpage.res
    rm masterpage.res.new
fi

cd ..
if [ ! -f "abping/abping.res" ]; then
    echo -n "Adding ABPing support ... "
    mkdir abping
    cd abping
    for file in abping element header line null; do
	wget -q "$WEBREP/res/abping/$file.res" -O "$file.res"
    done
    echo "done"
    echo ""
else
    cd abping
fi

wget -q "$WEBREP/update_res.sh" -O ../update_res.sh && chmod a+x ../update_res.sh

cd ../../../../../conf
grep "compression" server.xml &> /dev/null || (
    echo -n "Patching for gzip compression ... "
    cat server.xml | sed 's/<Connector port=\"8080\"/<Connector compression=\"on\" port=\"8080\"/' > server.xml.new
    mv server.xml.new server.xml
    echo "done"
    echo ""
)

cd ../webapps/axis/WEB-INF
wget -q "$WEBREP/WEBS/axis/WEB-INF/web.xml" -O "web.xml"

cd ../../ROOT

for file in colortable.gif info.jsp admin.jsp cache.jsp clear_cache.jsp dump_cache.jsp; do
    if [ ! -f "$file" ]; then
	wget -q "$WEBREP/WEBS/WEB_GRAPH/$file" -O "$file"
    fi
done

touch *.jsp

cat info.jsp | sed 's/lia.web.utils.ThreadedPage.lFirstRunDate/lia.web.utils.ThreadedPage.getFirstRunEpoch()/' > info.jsp_temp && mv info.jsp_temp info.jsp


cd WEB-INF/res

if [ ! -f "ColorsPicker1.htm" ]; then
    wget -q "$WEBREP/WEBS/WEB_GRAPH/WEB-INF/res/ColorsPicker1.htm" -O "ColorsPicker1.htm"
    wget -q "$WEBREP/WEBS/WEB_GRAPH/WEB-INF/res/ColorsPicker2.htm" -O "ColorsPicker2.htm"
fi

cd "$startdir/mysql_store" &>/dev/null

if [ $? -eq 0 ]; then
    MYSQLUPDATE=""
    wget -q "$WEBREP/mysql.version" -O "mysql.version"
    if [ `cat mysql.version` != `./current/bin/mysql --version | cut '-d ' -f 6` ]; then
	echo "MySQL versions differ : '`cat mysql.version`' : '`./current/bin/mysql --version | cut '-d ' -f 6`'"
        MYSQLUPDATE="true"
    else
	echo "MySQL is up to date"
        echo ""
    fi
    rm mysql.version

    if [ ! -z ${MYSQLUPDATE} ]; then
	echo -n "Updating MySQL daemon ... "
        wget -q "$WEBREP/mysql-latest.tar.gz" -O "mysql-latest.tar.gz" && (
	    rm -rf current latest v4.0.* &>/dev/null; 
	    tar -xzf mysql-latest.tar.gz;
	    ln -s latest current;
	    rm mysql-latest.tar.gz
	)
	echo "done"
	echo ""
    fi
fi

cd "$startdir"

TOMCATUPDATE=""
wget -q "$WEBREP/tomcat.version" -O "tomcat.version"
`cat conf/env.JAVA_HOME`/bin/jar xf tomcat/server/lib/catalina.jar org/apache/catalina/util/ServerInfo.properties
TOMCATVER=`cat org/apache/catalina/util/ServerInfo.properties | cut -d/ -f2`
rm -rf org
if [ `cat tomcat.version` != "${TOMCATVER}" ]; then
    echo "Tomcat must be updated (`cat tomcat.version` / $TOMCATVER)"
    TOMCATUPDATE="true"
else
    echo "Tomcat is up to date"
    echo ""
fi
rm tomcat.version

if [ ! -z ${TOMCATUPDATE} ]; then
    echo -n "Updating Tomcat ... "
    
    wget -q "$WEBREP/tomcat.tgz" -O "tomcat.tgz" && (
	rm tomcat/bin/*.jar tomcat/common/lib/*.jar tomcat/server/lib/*.jar
	tar -xzf tomcat.tgz;
	rm tomcat.tgz;
    )
    echo "done"
    echo ""
fi

cd "$startdir"

AXISUPDATE=""
wget -q "$WEBREP/axis.version" -O "axis.version"
jar xf lib/axis.jar META-INF/MANIFEST.MF
AXISVER=`cat META-INF/MANIFEST.MF | grep "Implementation-Version:" | cut -d: -f2-`
AXISVER=`echo ${AXISVER} | cut -c-11`
rm -rf META-INF
NEWAXIS=`cat axis.version | cut -c-11`
if [ "${NEWAXIS}" != "${AXISVER}" ]; then
    echo "Axis must be updated ($NEWAXIS / $AXISVER)"
    AXISUPDATE="true"
else
    echo "Axis is up to date"
    echo ""
fi
rm axis.version

if [ ! -z ${AXISUPDATE} ]; then
    echo -n "Updating Axis "
    
    for jar in axis commons-discovery commons-logging jaxrpc saaj wsdl4j; do
	wget -q "$WEBREP/${jar}.jar" -O "lib/${jar}.jar"
	echo -n "."
    done
    
    echo "  done"
    echo ""
fi

cd JStoreClient
cat njGlobal | sed 's/jcommon-\S*.jar/jcommon-1.0.6.jar/' | sed 's/jfreechart-\S*.jar/jfreechart-1.0.3.jar/' > njGlobal.tmp && mv njGlobal.tmp njGlobal

for jar in backport-util-concurrent jep-2.4.0 dnsjava-1.3.2 ymsg smack smackx cryptix jce-jdk13 jgsi puretls; do
    X=`cat njGlobal | grep "$jar.jar"`
    if [ -z "$X" ]; then
    	cat njGlobal | sed "s/MSRC_WEB.jar/MSRC_WEB.jar $jar.jar/" > njGlobal.tmp && mv njGlobal.tmp njGlobal
    fi
done

chmod a+x njGlobal
cd ..


echo "  You can now update the start/stop scripts too."
echo "  This is generally a good idea, but you will lose any changes"
echo "that you made to these scripts."
echo "  Please note that the default start/stop scripts assume you"
echo "are using PostgreSQL backend!"
echo ""
read -n 1 -t 15 -p "Do you want to upgrade the scripts? [y,N]: " UPGRADE
echo ""

if [ "x$UPGRADE" == "xy" -o "x$UPGRADE" == "xY" ]; then
    echo "Upgrading scripts ... "
    echo -n "   JStoreClient/njGlobal ... "
    wget $WEBREP/njGlobal -O JStoreClient/njGlobal -q
    chmod a+x JStoreClient/njGlobal
    echo "done"
    
    for script in \
	start.sh \
	stop.sh \
	update.sh \
	scripts/backup.sh \
	scripts/pgsql_console.sh \
	scripts/restart_jstoreclient.sh \
	scripts/start_jstoreclient.sh \
	scripts/start_pgsql.sh \
	scripts/stop_jstoreclient.sh \
	scripts/stop_pgsql.sh \
	scripts/testrun.sh \
	scripts/verify.sh \
    ; do
	echo -n "   $script ... "
	wget $WEBREP/scripts/$script -O $script -q
	chmod a+x $script
	echo "done"
    done
    
    echo "finished"
else
    echo "Not upgrading the scripts"
fi
echo ""
