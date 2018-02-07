#!/bin/bash

cd `dirname $0`

echo "Please edit mysql_console.sh first" > /dev/stderr
exit

### Remove the "echo" and the "exit" from above then fill the following 
### variables to match your configuration

PATH=../../mysql_store/current/bin
USER=mon_user
PASS=mon_pass
HOST=127.0.0.1
PORT=3306
DATABASE=mon_data

${PATH}/mysql -u ${USER} -p${PASS} -h ${HOST} -P ${PORT} ${DATABASE} $*
