#!/bin/bash

cd `dirname $0`

echo "SELECT distinct mfarm,mcluster,mnode,mfunction FROM monitor_1hour ORDER BY mfarm,mcluster,mnode,mfunction;" | ./mysql_console.sh --skip-column-names
