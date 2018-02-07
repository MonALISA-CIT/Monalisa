#!/bin/bash

cd `dirname $0`

FARMLIST=""
for farm in `./GetFarmNames.sh`; do
    if [ -z "${FARMLIST}" ]; then
	FARMLIST="${farm}"
    else
	FARMLIST="${FARMLIST},${farm}"
    fi
done

echo "Farms=${FARMLIST}" > ../../conf/website_config_files/global.properties
# add here any other parameters you need in the global.properties file
