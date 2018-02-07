#!/bin/sh

PRG_DIR=`dirname "$0"`
MonaLisa_HOME=`dirname "$0"`"/.."

LOG_FILE=${PRG_DIR}/"wgetML.log"
TMP_DIR=${PRG_DIR}"/WGET_ML_TMP"

#Please do not modify the following unless you know what you are doing
STD_ML_OK_LS_FILE=${PRG_DIR}/"ls_ML_ok.out"
STD_ML_LOCAL_LS_FILE=${PRG_DIR}/"ls_ML_local.out"
FILE_LIST=${PRG_DIR}/file_list
URL_FILE_LIST=${PRG_DIR}/url_list


echo -e "`date`: Starting ML_wget. Using ML_wget.log as log file and ${TMP_DIR} as temporary dir, ML_HOME=${MonaLisa_HOME} \n" > ${LOG_FILE} 2>&1
mkdir -p ${TMP_DIR} >> ${LOG_FILE} 2>&1

if [ $? -eq 0 ]; then 
  echo -e "`date`: ${TMP_DIR} CREATED! \n" >> ${LOG_FILE} 2>&1
 else
  echo -e "`date`: Cannot create temporary directory ${TMP_DIR}! ... Will exit now!\n" >> ${LOG_FILE} 2>&1
  echo -e "`date`: Cannot create temporary directory ${TMP_DIR}! ... Will exit now!\n"
  exit 1
fi

wget_cmd="wget -N -c -C off -x -P ${TMP_DIR}"

URL_PREFIX=`cat "${URL_FILE_LIST}"`


for URL in ${URL_PREFIX}; do
    
    CMD="wget -nd -c -N -C off ${URL}file_list"
    echo "Trying: ${CMD}"
    ${CMD} >> ${LOG_FILE} 2>&1
    CMD="wget -nd -c -N -C off ${URL}ls_ML_ok.out"
    echo "Trying: ${CMD}"
    ${CMD} >> ${LOG_FILE} 2>&1
    ML_JAR_LIST=`cat "${FILE_LIST}"`
    for JAR in ${ML_JAR_LIST}; do
	CMD="${wget_cmd} ${URL}${JAR}"
	echo "Trying: ${CMD}"
	${CMD} >> ${LOG_FILE} 2>&1
    done
done

echo -e "`date`: Finish getting the files...Trying ls \n" >> ${LOG_FILE} 2>&1
find ${TMP_DIR} -name *.jar|xargs wc -c|grep jar|\
	while read LINE; do COLS=($LINE); 
		echo "${COLS[0]} `basename ${COLS[1]}`"; 
	done|\
	sort -nr --key=1 >>${LOG_FILE} 2>&1
# ls -1sRh ${TMP_DIR}|grep jar|sort -n >> ${LOG_FILE} 2>&1

echo -e "`date`: Finish ls...Trying to write to ls_local.out \n" >> ${LOG_FILE} 2>&1
find ${TMP_DIR} -name *.jar|xargs wc -c|grep jar|\
        while read LINE; do COLS=($LINE);
              echo "${COLS[0]} `basename ${COLS[1]}`";
        done|\
        sort -nr --key=1 >${STD_ML_LOCAL_LS_FILE} 2>>${LOG_FILE}

# ls -1sRh ${TMP_DIR}|grep jar|sort -n > ${STD_ML_LOCAL_LS_FILE} 2>>${LOG_FILE}

echo -e "`date`: Trying diff...\n" >> ${LOG_FILE} 2>&1
diff ${STD_ML_OK_LS_FILE} ${STD_ML_LOCAL_LS_FILE} >>${LOG_FILE} 2>&1

diff ${STD_ML_OK_LS_FILE} ${STD_ML_LOCAL_LS_FILE}

if [ $? -eq 0 ]; then
  echo "`date`: Update OK! No diff found." >>${LOG_FILE} 2>&1
  echo "`date`: Update OK! No diff found. Will update MonALISA binaries! Please wait ..."
 else
  echo "`date`: Update NOT OK!Plese see ${LOG_FILE}" >>${LOG_FILE} 2>&1
  echo "`date`: Update NOT OK!Plese see ${LOG_FILE}"
  exit 1
fi

echo "`date`: Trying TO copy the downloaded files" >>${LOG_FILE} 2>&1

CP_CMD="cp -fr ${TMP_DIR}/monalisa.cacr.caltech.edu/FARM_ML/Service/* ${MonaLisa_HOME}/Service/"
echo "`date`: Trying [${CP_CMD}]" >>${LOG_FILE} 2>&1
${CP_CMD} 2>>${LOG_FILE}

CP_CMD="cp -fr ${TMP_DIR}/monalisa.cacr.caltech.edu/FARM_ML/Control/* ${MonaLisa_HOME}/Control/"
echo "`date`: Trying [${CP_CMD}]" >>${LOG_FILE} 2>&1
${CP_CMD} 2>>${LOG_FILE}

echo "`date`: Copy finished! MonaLisa_HOME is:  " >> ${LOG_FILE} 2>&1
ls -laR >> ${LOG_FILE} 2>&1


echo "`date`: Trying to remove temporary directories:  " >> ${LOG_FILE} 2>&1

CMD="rm -r ${TMP_DIR}"
echo "`date`: Trying CMD [ $CMD ] " >> ${LOG_FILE} 2>&1
${CMD} >> ${LOG_FILE} 2>&1

CMD="rm -r ${MonaLisa_HOME}/Service/TMP_UPDATE"
echo "`date`: Trying CMD [ $CMD ] " >> ${LOG_FILE} 2>&1
${CMD} >> ${LOG_FILE} 2>&1

CMD="rm -r ${STD_ML_LOCAL_LS_FILE}"
echo "`date`: Trying CMD [ $CMD ] " >> ${LOG_FILE} 2>&1
${CMD} >> ${LOG_FILE} 2>&1

exit 0
