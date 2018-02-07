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
# Log file for your server
#
LOG_FILE=$MYSQL_HOME/Start_Mysql_history_log

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

$MYSQL_HOME/current/bin/mysql -u root -S $MYSQL_HOME/var/mysql.sock < $MYSQL_HOME/Conf/InitMonaLisa/createDB.sql
