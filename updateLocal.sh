#!/bin/bash

cd `dirname $0`

cp \
    WEBS/WEB_GRAPH/WEB-INF/lib/MSRC_WEB.jar \
    MonaLisa/Clients/lib/JStoreClient.jar \
    /home/costing/MLrepository/lib/

cd ~/MLrepository/scripts/

./restart_jstoreclient.sh

cd ~/MLrepository/JStoreClient/
echo -n > log.out

tail -f log.out
