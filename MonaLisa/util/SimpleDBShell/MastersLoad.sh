#!/bin/bash

cd `dirname $0`

echo
echo "Last known Load5 values"
echo "-----------------------"
echo

for farm in `echo "SELECT distinct mfarm FROM monitor_1hour ORDER BY mfarm ASC;" | ./mysql_console.sh --skip-column-names`; do
    echo "  Farm : $farm"
    
    rectime=`echo "SELECT max(rectime) FROM monitor_1hour WHERE mfarm='$farm' AND mcluster='Master' AND mfunction='Load5';" | ./mysql_console.sh --skip-column-names`
    
    val=`echo "SELECT mval FROM monitor_1hour WHERE mfarm='$farm' AND mcluster='Master' AND mfunction='Load5' AND rectime='$rectime';" | ./mysql_console.sh --skip-column-names`

    echo "      Load5 : $val"
    echo
done
