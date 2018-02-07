#!/bin/bash

echo -n "Are you sure you want to clear the mysql database ? [yes/NO] : "
read ok

if [ "${ok}" != "yes" -a "${ok}" != "YES" ]; then
    exit
fi

cd `dirname $0`

./stop_tomcat.sh &>/dev/null
./stop_jstoreclient.sh &>/dev/null
./stop_mysql.sh &>/dev/null
sleep 10
./start_mysql.sh &>/dev/null
sleep 10

tables=`echo "show tables;" | ./mysql_console.sh | grep -v "Tables_in_mon_data"`

for t in $tables; do
    echo "drop table ${t};" | ./mysql_console.sh
done

./stop_mysql.sh &>/dev/null

echo "If you haven't seen any errors above than your database should be cleared now."
echo "All the services were stopped in the process."
