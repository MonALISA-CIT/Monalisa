#!/bin/bash

cd `dirname $0`

if [ ! -z ${MONALISA_WS} ]; then
    echo -n ${MONALISA_WS}
else
    cat env.MONALISA_WS
fi
