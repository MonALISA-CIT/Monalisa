#!/bin/bash

cd `dirname $0`

./stop_mysql.sh
./start_mysql.sh
