#!/bin/bash

cd `dirname $0`

ML2=repository@monalisa2:/home/repository/MLrepository
ALICE=monalisa@pcalimonitor:/home/monalisa/MLrepository

for rep in $ALICE; do
    scp \
	WEBS/WEB_GRAPH/WEB-INF/lib/MSRC_WEB.jar \
	MonaLisa/Clients/lib/JStoreClient.jar \
    $rep/lib
done
