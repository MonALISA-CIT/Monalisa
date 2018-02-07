#!/bin/bash

cd `dirname $0`

PORT=`cat Start_Mysql.sh | grep -E -e "^MYSQL_TCP_PORT=" | cut -d= -f2`

PID=`netstat -ltpn 2>/dev/null | grep -E -e ":$PORT[^0-9]+" | grep mysqld | sed -r 's/ +/ /g' | cut -d\  -f7 | cut -d/ -f1`

kill -USR1 "$PID" &>/dev/null
