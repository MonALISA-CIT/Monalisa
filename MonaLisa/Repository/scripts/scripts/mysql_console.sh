#!/bin/bash

cd `dirname $0`

export PATH="$PATH:/sbin:/usr/sbin:/usr/local/sbin"

# this script assumes default values for the database connection
# should anything be different feel free to change this file

../mysql_store/current/bin/mysql -u mon_user -pmon_pass -h 127.0.0.1 -P 3306 mon_data
