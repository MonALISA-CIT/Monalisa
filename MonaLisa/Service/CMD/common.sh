#!/bin/sh
#
# ML Script Version:1.8.2
#

#
# 
# DO NOT CHAGE THIS FILE!
# This script is called from CHECK_UPDATE and ML_SER
#
# 03/02/09 add evo env
#

if [ -z "${PRGDIR}" ]; then
    echo 'This script assumes that variable PRGDIR is set'
    echo 'It should be called from the other scripts'
    exit 1
fi

# Load the ML environment from ml_env and site_env
ML_ENV_FILE="${CONFDIR}"/"ml_env"
SITE_ENV_FILE="${CONFDIR}"/"site_env"
EVO_ENV_FILE="${CONFDIR}"/"mlevo_env"

if [ -r "${ML_ENV_FILE}" ]; then
    . "${ML_ENV_FILE}"
else
    echo "Cannot find ${ML_ENV_FILE}"
    exit 1
fi

if [ -r "${SITE_ENV_FILE}" ]; then
    . "${SITE_ENV_FILE}"
fi

if [ -r "${EVO_ENV_FILE}" ]; then
    . "${EVO_ENV_FILE}"
fi

# Do some basic checks for the env variables
check_env(){
if [ -z "${JAVA_HOME}" ]; then
    echo "Please set JAVA_HOME in ${ML_ENV_FILE}"
    return 1
fi

if [ ! -x "${JAVA_HOME}/bin/java" ]; then
    echo "Your JAVA_HOME ( ${JAVA_HOME} ) in ${ML_ENV_FILE} seems to be wrong"
    echo "Cannot execute ${JAVA_HOME}/bin/java"
    return 1
fi

if [ -z "${FARM_HOME}" ]; then
    echo "Please set FARM_HOME in ${ML_ENV_FILE}"
    return 1
fi

if [ ! -d "${FARM_HOME}" ]; then
    echo "Your FARM_HOME ( ${FARM_HOME} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "FARM_HOME should be a directory!"
    return 1
fi

if [ ! -r "${FARM_HOME}" ]; then
    echo "Your FARM_HOME ( ${FARM_HOME} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "${FARM_HOME}! does not have read access!"
    return 1
fi

if [ ! -w "${FARM_HOME}" ]; then
    echo "Your FARM_HOME ( ${FARM_HOME} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "${FARM_HOME}! does not have write access!"
    return 1
fi

ML_PID_FILE="${FARM_HOME}"/".ml.pid"

if [ -z "${FARM_CONF_FILE}" ]; then
    echo "Please set FARM_CONF_FILE in ${ML_ENV_FILE}"
    return 1
fi

if [ ! -r "${FARM_CONF_FILE}" ]; then
    echo "Your FARM_CONF_FILE ( ${FARM_CONF_FILE} ) in ${ML_ENV_FILE} seems to be wrong"
    echo "${FARM_CONF_FILE} does not have read access"
    return 1
fi

if [ -z "${FARM_NAME}" ]; then
    echo "Please set FARM_NAME in ${ML_ENV_FILE}"
    return 1
fi

if [ -z "${CACHE_DIR}" ]; then
    echo "Your CACHE_DIR ( ${CACHE_DIR} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "CACHE_DIR should be a directory!"
    return 1
fi

#maybe it's the first time with update...
if [ ! -d "${CACHE_DIR}" ]; then
    return 0
fi

if [ ! -r "${CACHE_DIR}" ]; then
    echo "Your CACHE_DIR ( ${CACHE_DIR} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "CACHE_DIR does not have read access!"
    return 1
fi
                                                                                                                                               
if [ ! -w "${CACHE_DIR}" ]; then
    echo "Your CACHE_DIR ( ${CACHE_DIR} ) in ${ML_ENV_FILE} seems to be a wrong"
    echo "CACHE_DIR does not have write access!"
    return 1
fi

}
