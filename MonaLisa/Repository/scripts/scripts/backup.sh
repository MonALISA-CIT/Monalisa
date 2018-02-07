#!/bin/bash
cd `dirname $0`

cd ..

DIR="backup"
mkdir -p "$DIR"

FILE=`date '+%Y%m%d-%H%M'`

tar \
    -czf "$DIR/$FILE.tar.gz" \
    --exclude tomcat/webapps/ROOT/backup.tar.gz \
    --exclude tomcat/webapps/ROOT/WEB-INF/classes/lia \
    --exclude-from scripts/backup.exclude \
    --files-from scripts/backup.include \
    tomcat/webapps \
    tomcat/conf \
    JStoreClient/conf

cp -f "$DIR/$FILE.tar.gz" tomcat/webapps/ROOT/backup.tar.gz
