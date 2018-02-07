#!/bin/bash

print(){
    echo "    Avg : $1"
    echo "    Min : $2"
    echo "    Max : $3"
    echo
}

cd `dirname $0`

echo
echo "Average, Min and Max Load5 values for the last hour"
echo "--------------------------------------"
echo

for farm in `echo "SELECT distinct mfarm FROM monitor_1hour ORDER BY mfarm ASC;" | ./mysql_console.sh --skip-column-names`; do
    echo "  Farm : $farm"
    
    avg=`echo "SELECT avg(mval),min(mval),max(mval) FROM monitor_1hour WHERE mfarm='$farm' AND mcluster='Master' AND mfunction='Load5';" | ./mysql_console.sh --skip-column-names`

    print $avg    
done
