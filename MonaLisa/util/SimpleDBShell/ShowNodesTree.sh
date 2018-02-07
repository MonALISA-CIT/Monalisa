#!/bin/bash

cd `dirname $0`

for farm in `echo "SELECT distinct mfarm FROM monitor_1hour ORDER BY mfarm ASC;" | ./mysql_console.sh --skip-column-names`; do
    echo "Farm : $farm"
    
    for cluster in `echo "SELECT distinct mcluster FROM monitor_1hour WHERE mfarm='$farm' ORDER BY mcluster ASC;" | ./mysql_console.sh --skip-column-names`; do
	echo "  Cluster : $cluster"
	
	for node in `echo "SELECT distinct mnode FROM monitor_1hour WHERE mfarm='$farm' AND mcluster='$cluster' ORDER BY mnode ASC;" | ./mysql_console.sh --skip-column-names`; do
	    echo "    Node : $node"
	done
    done
done
