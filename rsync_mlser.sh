#!/bin/bash

#
# The following env variables have to be set by external means
#
# APP_NAME=MLService 
# APP_VERSION=1.9.0 
# LOCAL_DIR=build/dist/web/MonALISA_Service/
# REMOTE_SITES="monalisa.cern.ch monalisa.caltech.edu"
# REMOTE_USER=mlupdate
# REMOTE_DIR=public_html/FARM_ML/
#

if [ "${LOCAL_DIR}" == "" -o "${REMOTE_USER}" == "" -o "${REMOTE_SITES}" == "" -o  "${REMOTE_DIR}" == "" -o "${APP_VERSION}" == "" -o "${APP_NAME}" == "" ]; then
    echo "Please set the folowing env vars LOCAL_DIR REMOTE_SITES REMOTE_USER REMOTE_DIR APP_NAME APP_VERSION"
    exit 7
fi

echo "Local env is ok:"
echo "APP_VERSION = ${APP_VERSION}"
echo "APP_NAME = ${APP_NAME}"
echo "REMOTE_SITES = ${REMOTE_SITES}"
echo "REMOTE_USER = ${REMOTE_USER}"
echo "LOCAL_DIR = ${LOCAL_DIR}"
echo "REMOTE_DIR = ${REMOTE_DIR}"
echo "Generating the update manifest"
java -cp build/classes -Dlia.Monitor.ConfigURL=file:MonaLisa/updateBuilder.properties lia.util.update.builder.UpdaterBuilder 

if [ $? -ne 0 ]; then
  echo "[ ERROR ] Unable to generate the updater manifest"
  exit 5
fi

echo "[ OK ] Generating the update manifest"
echo "Checking local files against MD5 sums"

cdir=`pwd`
cd ${LOCAL_DIR}
md5sum -c allFiles.md5sum
if [ $? -ne 0 ]; then
  echo "[ ERROR ] Checking local files against MD5 sum failed"
  exit 5
fi
echo "[ OK ] Local MD5 sums"
cd ${cdir}
RSYNC_OPTS="-v -a --delay-updates --compress --recursive --times --perms"
RSYNC_EXCLUDES="*.jnlp"

for site in ${REMOTE_SITES}; do
    echo " Synchronizing remote URL: ${REMOTE_USER}@${site}:${REMOTE_DIR} with local dir: ${LOCAL_DIR}"
    rsync ${RSYNC_OPTS} --exclude "${RSYNC_EXCLUDES}" ${LOCAL_DIR} ${REMOTE_USER}@${site}:${REMOTE_DIR}
    if [ $? -eq 0 ]; then
        echo "Verifying remote MD5 checksum ... "
    
        REMOTE_CMD="cd ${REMOTE_DIR}/${APP_NAME}_${APP_VERSION} 
            if [ \$? -ne 0 ]; then 
                echo ===[ REMOTE:${site} ]=====[ ERROR ] Unable to change dir to ${REMOTE_DIR}/${APP_NAME}_${APP_VERSION}/ =======  
                exit 4 
            fi 
            md5sum -c allFiles.md5sum 
            if [ \$? -eq 0 ]; then 
                echo ===[ REMOTE:${site} ]=====[ OK ] MD5 CHECKSUM ON REMOTE SITE: ${site} =======  
            else 
                echo ===[ REMOTE:${site} ]=====[ ERROR ] MD5 CHECKSUM NOT OK ON REMOTE SITE: ${site} =======  
                exit 1 
            fi"
        
        ssh ${REMOTE_USER}@${site} "${REMOTE_CMD}"
        if [ $? -ne 0 ]; then
            exit 1;
        fi
        echo "[ OK ] FINISHED Synchronizing remote URL: ${REMOTE_USER}@${site}:${REMOTE_DIR} "
    else 
        exit 2;
    fi
done

echo "[ OK ]The remote sites: ${REMOTE_USER} @ ${REMOTE_SITES} : ${REMOTE_DIR} are now synchronized"
echo "Creating symlinks"

for site in ${REMOTE_SITES}; do

    REMOTE_CMD_SYMLINK="cd ${REMOTE_DIR}
        if [ \$? -ne 0 ]; then 
            echo ===[ REMOTE:${site} ]=====[ ERROR ] Unable to change dir to ${REMOTE_DIR} =======  
            exit 4 
        fi
        ln -sfn ${APP_NAME}_${APP_VERSION} ${APP_NAME} ;
        if [ \$? -eq 0 ]; then 
            echo ===[ REMOTE:${site} ]=======[ OK ] symlink ${APP_NAME} '->' ${APP_NAME}_${APP_VERSION} created in ${REMOTE_DIR}/ for ${site} ======= 
        else 
            echo ====[ REMOTE:${site} ]======[ ERROR ] Unable to create symlink ${APP_NAME} '->' ${APP_NAME}_${APP_VERSION} in ${REMOTE_DIR}/ for ${site} ======= 
            exit 8 
        fi"
    ssh ${REMOTE_USER}@${site} "${REMOTE_CMD_SYMLINK}"
    if [ $? -ne 0 ]; then
        echo "[ ERROR ] Unable to create symlink on remote site: ${site}"
        exit 8
    fi
done