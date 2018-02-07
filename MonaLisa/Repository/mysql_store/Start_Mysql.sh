#
#Suggestions: ramiro@flames.roedu.net
#
#
# Quick start:
#  Set MYSQL_HOME, MYSQL_TCP_PORT
#
#
#
#

#########################################################
#
# START CONFIGURABLE PARAMETERS
#
#########################################################

#
# Ex:

cd `dirname $0`

MYSQL_HOME="`cat ../conf/env.REPOSITORY_DIR`/mysql_store"
#
#MYSQL_HOME=< PLEASE SET THIS PARAMETER as the path to mysql_store >

#
# TCP port on which your server we'll listen
# Default value for mysql: 3306
#
MYSQL_TCP_PORT=3316
MYSQL_TCP_HOST=127.0.0.1

#
# Log file for your server
#
LOG_FILE=$MYSQL_HOME/Mysql.log


#
#
# The current distribution. It's a good practice to keep this as a link to
# whatever distribution WITH InnoDB you are using!
# In the default distro it was created using the following command:
# ln -sf v3.23.51_InnoDB current
#
MYSQL_CURRENT=$MYSQL_HOME/current

#
#
# Path to libraries used by the server
# PLEASE DO NOT MODIFY, unless you know what you are doing.
#
export LD_LIBRARY_PATH=$MYSQL_CURRENT/lib

#
#
# It's a good practice to keep Mysql system data (users, passwords) separated
# from the version ( in $MYSQL_HOME/data). When you'll upgrade to a new version
# the only thing you have to do is to make the link ( $MYSQL_HOME/current ) to point
# to your new version. Your user/passwords configurations we'll be the same.
#
MYSQL_DATA_DIR=$MYSQL_HOME/data
MYSQL_SOCKET_DIR=$MYSQL_HOME/var
MYSQL_SOCKET_NAME=mysql.sock

INNODB_HOME=$MYSQL_HOME/InnoDB
INNODB_DATA_DIR=$INNODB_HOME/data

#
# The default database starts with 50Mbytes alocated.
# Do not remove the autoextend parameter!!!!
#
INNODB_FILE_PARAMETER_STRING=ibdata1:50M:autoextend

#
#
# The folowing parameters are used by InnoDB.
# PLEASE DO NOT MODIFY, unless you know what you are doing!
#
INNODB_LOGS_DIR=$INNODB_HOME/logs
INNODB_LOGS_ARCH_DIR=$INNODB_LOGS_DIR
INNODB_LOG_FILE_SIZE=10M
INNODB_LOG_FILES_IN_GROUP=3


mysqld_command=`echo $MYSQL_CURRENT/bin/mysqld`
install_db_script=`echo $MYSQL_CURRENT/scripts/mysql_install_db`

#############################################
#
# PERFORMANCE PARAMETERS
#
#############################################

#
# The bigger values...the better!
#
INNODB_BUFFER_POOL_SIZE=32M
INNODB_ADDITIONAL_MEM_POOL_SIZE=32M
INNODB_LOG_BUFFER_SIZE=32M


#########################################################
#
# END CONFIGURABLE PARAMETERS
#
#########################################################


SETCOLOR_SUCCESS="echo -en \\033[1;32m"
SETCOLOR_NORMAL="echo -en \\033[0;39m"
SETCOLOR_FAILURE="echo -en \\033[1;31m"
SETCOLOR_INFO="echo -en \\033[1;33m"

echo_success() {
  $SETCOLOR_SUCCESS
  echo -n " OK "
  $SETCOLOR_NORMAL
  echo 
  return 0
}

echo_info() {
  $SETCOLOR_INFO
  echo -n " INFO "
  $SETCOLOR_NORMAL
  return 0
}

echo_failure() {
  $SETCOLOR_FAILURE
  echo -n " FAIL "
  $SETCOLOR_NORMAL
  echo 
  return 1
}

pid=`netstat -ltn | grep -E -e ":$MYSQL_TCP_PORT[^0-9]+"`

if [ ! -z "$pid" ]; then
 echo_info && echo -n "Another program is bind to port $MYSQL_TCP_PORT"
 echo
 echo_info && echo -n "Please edit the configuration files and change the TCP port for MySQL"
 echo
 echo_failure
 exit 1
fi

echo Starting with $cmd >> $LOG_FILE 2>&1
echo "************************************" >> $LOG_FILE 2>&1
echo "* Starting Mysql (`date`)"			>> $LOG_FILE 2>&1
echo "************************************" >> $LOG_FILE 2>&1

echo_info && echo -n "Using MySql base dir in $MYSQL_CURRENT"
if  [ ! -d $MYSQL_HOME ] || [ ! -d $MYSQL_CURRENT ]; then
	echo_failure 
	echo Please set the correct MYSQL_HOME and MYSQL_CURRENT in $0
	exit 1
fi

echo_success

########################################
# IS IT THE FIRST TIME?
########################################
if [ ! -d $MYSQL_DATA_DIR/mysql ]; then
	echo_info && echo -n "Installing Mysql Data files in $MYSQL_DATA_DIR"
	
	if [ ! -x $install_db_script ]; then
		echo_failure
		echo Cannot initialize Mysql Data files in $MYSQL_DATA_DIR
		echo Please set the correct value for install_db_script	in $0 
		exit 1
	fi

	cmd=`echo $install_db_script --basedir=$MYSQL_CURRENT --ldata=$MYSQL_DATA_DIR` 
	
	$cmd >> $LOG_FILE 2>&1

	if [ $? -ne 0 ]; then
		echo_failure
		echo Error executing $cmd
		echo '########################################'
		echo_info && echo Check the logs in $LOG_FILE
		echo '########################################'
		exit 1
	fi
echo_success
fi

echo_info && echo Using Mysql Data files from $MYSQL_DATA_DIR

#####################################
#
# Checking directories for InnoDB
#
#####################################

echo_info && echo -n "Checking directories layout for InnoDB"

if [ ! -d $INNODB_HOME ] || [ ! -d $INNODB_DATA_DIR ] || [ ! -d $INNODB_LOGS_DIR ] || [ ! -d $INNODB_LOGS_ARCH_DIR ]; then
 echo_info && echo -n "First time? Trying to create..."

 echo "Trying to create InnoDB directories" >> $LOG_FILE
 cmd=`echo mkdir $INNODB_HOME`
 echo Executing: $cmd >> $LOG_FILE 2>&1
 $cmd >> $LOG_FILE 2>&1

 cmd=`echo mkdir $INNODB_DATA_DIR`
 echo Executing: $cmd >> $LOG_FILE 2>&1
 $cmd >> $LOG_FILE 2>&1

 cmd=`echo mkdir $INNODB_LOGS_DIR`
 echo Executing: $cmd >> $LOG_FILE 2>&1
 $cmd >> $LOG_FILE 2>&1

 cmd=`echo mkdir $INNODB_LOGS_ARCH_DIR`
 echo "Executing: $cmd" >> $LOG_FILE 2>&1
 $cmd >> $LOG_FILE 2>&1

 if [ ! -d $INNODB_HOME ] || [ ! -d $INNODB_DATA_DIR ] || [ ! -d $INNODB_LOGS_DIR ] || [ ! -d $INNODB_LOGS_ARCH_DIR ]; then
		echo_failure
		echo '########################################'
		echo_info && echo Check the logs in $LOG_FILE
		echo '########################################'
	exit 1;
 fi
 echo Success in creating InnoDB directories layout >> $LOG_FILE 2>&1
fi
echo_success

######################################
#$MYSQL_SOCKET_DIR for $MYSQL_SOCKET
######################################

MYSQL_SOCKET=`echo $MYSQL_SOCKET_DIR/$MYSQL_SOCKET_NAME`
echo_info && echo -n Checking for $MYSQL_SOCKET_DIR

if [ ! -d $MYSQL_SOCKET_DIR ]; then
 echo
 echo_info && echo "$MYSQL_SOCKET_DIR doesn't exist. Trying to create it..."

 cmd=`echo mkdir -p $MYSQL_SOCKET_DIR`
 echo "Executing: $cmd" >> $LOG_FILE 2>&1
 $cmd >> $LOG_FILE 2>&1

 if [ ! -d $MYSQL_SOCKET_DIR ]; then
	echo_failure
	echo '########################################'
    echo_info && echo Check the logs in $LOG_FILE
    echo '########################################'
    exit 1
 fi
 echo "Success creating $MYSQL_SOCKET_DIR" >> $LOG_FILE 2>&1
fi
echo_success


###############################
# General parameters for mysqld
###############################

mysqld_parameters="-b $MYSQL_CURRENT -h $MYSQL_DATA_DIR --socket=$MYSQL_SOCKET --bind-address=$MYSQL_TCP_HOST --port=$MYSQL_TCP_PORT --lower_case_table_names=1 --skip-bdb --max_allowed_packet=16M --skip-log-warnings "

#InnoDB specific

inodb_specific="--innodb_data_home_dir=$INNODB_DATA_DIR --innodb_data_file_path=$INNODB_FILE_PARAMETER_STRING --innodb_log_group_home_dir=$INNODB_LOGS_DIR --innodb_log_arch_dir=$INNODB_LOGS_ARCH_DIR"
innodb_logs_params="--innodb_log_file_size=$INNODB_LOG_FILE_SIZE --innodb_log_files_in_group=$INNODB_LOG_FILES_IN_GROUP" 
inodb_performance_params="--innodb_log_buffer_size=$INNODB_LOG_BUFFER_SIZE --innodb_buffer_pool_size=$INNODB_BUFFER_POOL_SIZE --innodb_additional_mem_pool_size=$INNODB_LOG_BUFFER_SIZE"

params="$mysqld_parameters $inodb_specific $innodb_logs_params $inodb_performance_params"

echo_info && echo -n Starting Mysql

cmd=`echo $mysqld_command $params`
echo Starting with $cmd >> $LOG_FILE 2>&1

$cmd >> $LOG_FILE 2>&1&
sleep 1

pid=`ps -a | grep mysqld | wc -l`

if [ $pid ]; then
	echo_success
	echo "`date` Mysql SUCCESSFULLY STARTED" >> $LOG_FILE 2>&1&
else
	echo_failure
	echo "`date` Mysql DID NOT START!!!!" >> $LOG_FILE 2>&1&
fi
