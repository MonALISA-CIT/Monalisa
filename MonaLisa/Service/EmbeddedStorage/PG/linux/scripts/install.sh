#!/bin/bash

cd `dirname $0`

export PATH=$PATH:/sbin:/usr/sbin:/usr/local/sbin

gunzip < pgsql.tgz | tar xf - || exit 2
