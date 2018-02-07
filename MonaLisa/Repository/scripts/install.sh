#!/bin/bash

export PATH=$PATH:/sbin:/usr/sbin:/usr/local/sbin

myreplace(){
    a=`echo $1 | sed 's/\//\\\\\//g'`
    b=`echo $2 | sed 's/\//\\\\\//g'`
    
    cat | sed "s/$a/$b/g"
}

java=`which java | grep -v -E -e "^which: no"`

while [ -z "${java}" ]; do
    echo -n "Path to java home directory: "
    read jt
    if [ -x "${jt}/bin/java" ]; then
	echo "The path you specified seems to be ok."
	java="${jt}/bin/java"
    else
	echo "No java executable was found in the path you specified. Please do not include /bin or /bin/java in the path."
    fi
done

java=`dirname "${java}"`
java=`dirname "${java}"`

cd `dirname $0`

DIR="`pwd`"

echo ${java} > conf/env.JAVA_HOME || exit
echo ${DIR} > conf/env.REPOSITORY_DIR || exit
echo -n  "http://localhost:8080/axis/services/MLWebService" > conf/env.MONALISA_WS || exit

ACCOUNT=`id -u -n`

if [ "`id -u`" -eq 0 ]; then
    ACCOUNT=""

    echo "MLrepository cannot be run as root."
    echo "Please specify an alternate account from which MLrepository will run."
    
    AT=`pwd | cut -d/ -f3`
    
    while [ -z "$ACCOUNT" ] ; do
	echo -n "Account name [$AT] : ";
	read ATT;
	
	if [ ! -z "$ATT" ]; then
	    AT="$ATT"
	fi
	
	if [ "`id -u -n "$AT" 2>/dev/null`" == $AT ]; then
	    ACCOUNT=$AT
	else
	    echo "Hmmm ... the specified account doesn't seem to exist ..."
	fi
    done
fi

echo "$ACCOUNT" > conf/env.ACCOUNT || exit

chown "$ACCOUNT" "$DIR" -R || exit

cat init.d/repository | \
    myreplace "__mluser__" "$ACCOUNT" | \
    myreplace "__mlpath__" "$DIR" \
> init.d/repository.tmp
rm -f init.d/repository
mv init.d/repository.tmp init.d/repository

chmod a+x start.sh stop.sh update.sh init.d/repository scripts/*.sh || exit
chmod a-x scripts/*mysql* &>/dev/null

#scripts/start_mysql.sh || exit
#(cd mysql_store && ./InitMonaLisa.sh) || exit
#scripts/stop_mysql.sh || exit

pgsql_store/init.sh
killall sleep
#echo -n "Stopping PostgreSQL server ... "
scripts/stop_pgsql.sh
#echo "done"

chmod a-x pgsql_store/init.sh

# configure LazyJ-based classes with the real location of the repository
tomcat/webapps/ROOT/WEB-INF/classes/compile.sh

# other pages that need configuring
for F in tomcat/webapps/ROOT/info.jsp tomcat/webapps/ROOT/WEB-INF/conf/repository/base.properties; do
	sed "s#pcalimonitor.cern.ch:8889#`hostname -f`%#g" <$F >$F.new && mv $F.new $F
done

F=JStoreClient/conf/App.properties
sed "s#/home/monalisa/MLrepository/pgsql_store#`pwd`/pgsql_store/data#g" <$F >$F.new && mv $F.new $F

mkdir -p logs/lazyj &>/dev/null

rm -f ./InitMonaLisa.sh

echo ""
echo ""
echo "Installation completed successfully."
echo "You can run start.sh now, but please take a look first at the files in conf/ directory"
echo ""

rm `basename $0`
