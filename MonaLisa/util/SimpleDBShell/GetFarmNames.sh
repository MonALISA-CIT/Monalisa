#!/bin/bash

cd `dirname $0`

echo "SELECT distinct mfarm FROM monitor_10hour ORDER BY mfarm ASC;" | ./mysql_console.sh --skip-column-names
