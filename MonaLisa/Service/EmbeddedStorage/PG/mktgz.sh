#!/bin/bash

if [ -z "${PGOSName}" ]; then
 echo Unable to determine \$PGOSName
 exit 2
fi

if [ -z "${PGArch}" ]; then
 echo Unable to determine \$PGArch
 exit 2
fi

if [ -z "${PGVersion}" ]; then
 echo Unable to determine \$PGVersion
 exit 2
fi


cd `dirname $0`/${PGOSName}/${PGArch}

echo Using PGVersion: ${PGVersion}
PG_UNTAR_DIR="pgsql_store_${PGVersion}_${PGArch}"
TGZ_NAME="${PG_UNTAR_DIR}.tgz"

rm -rf ${PG_UNTAR_DIR}
rm -rf pgsql

gunzip < ${TGZ_NAME} | tar xf - || exit 2

mv ${PG_UNTAR_DIR} pgsql || exit 3
cp ../scripts/*.sh pgsql || exit 3
cp ../scripts/fuser_${PGArch} pgsql/fuser || exit 3
mv pgsql/install.sh . || exit 3

chmod 755 pgsql/*.sh
chmod 755 install.sh
chmod 755 pgsql/fuser

tar czf pgsql.tgz pgsql

rm -rf ${PG_UNTAR_DIR}
rm -rf pgsql

echo "os: "${PGOSName} > MANIFEST.MF
echo "arch: "${PGArch} > MANIFEST.MF
