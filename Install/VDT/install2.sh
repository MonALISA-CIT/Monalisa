#!/bin/bash

VERS=1.1
MD5="b2775b53ae376432bdaa60cefc6d6b38  MonaLisa.v1.1.tar.gz"

DIST=VDTFarm

myreplace(){
    a=`echo $1 | sed 's/\//\\\\\//g'`
    b=`echo $2 | sed 's/\//\\\\\//g'`
    
    cat | sed "s/$a/$b/g"
}

if [ -z "$1" -o -z "$2" ]; then
    echo "This script is not ment to be runed manually"
    echo "Please use the install.sh script"
    exit
fi

cd `dirname $0`
WD=`pwd`

echo "$MD5" | md5sum --status --check - 

if [ $? -ne 0 ]; then
    echo "Invalid md5 checksum. please download the MonALISA archive again."
    exit 1
fi

INSTALL="$2/MonaLisa"

echo -n "Where do you want MonaLisa installed ? [$INSTALL] : "
read INST
if [ ! -z "$INST" ]; then
    INSTALL="$INST"
fi

if [ -e $INSTALL ]; then
    if [ ! -d $INSTALL ]; then
	echo "The destination exists and it is not a folder !"
	exit
    fi
    
    echo "The destination already exists."
    echo "This script will keep only the CMD, TEST and ${DIST} folders from a previous installation of MonaLisa"
    echo -n "Are you sure you want MonaLisa installed here (y/n)? [n]: "
    read SURE
    if [ ! "$SURE" == "y" ]; then
	echo "The install script will exit now"
	echo "Please rerun the install script and specify a different folder"
	exit
    fi
fi

mkdir -p "$INSTALL"

cd "$INSTALL"
tar -xzf $WD/MonaLisa.v1.1.tar.gz

cd MonaLisa.v${VERS}

CLEAN="true"

for a in *; do
    if [ -e "../$a" ]; then
	if [ "$a" == "Service" ]; then
	    mkdir ../Service &>/dev/null
	    for b in Service/* ; do
		if [ "$b" == "Service/CMD" -o "$b" == "Service/TEST" -o "$b" == "Service/${DIST}" ]; then
		    if [ -e "../$b" ]; then
			#echo "keeping $b"
			CLEAN="false"
		    else
			mv $b ../Service
		    fi
		else
		    rm -rf "../$b"
		    mv $b ../Service
		fi
	    done
	else
	    rm -rf "../$a"
	    mv $a ..
	fi
    else
	mv $a ..
    fi
done

cd ..
rm -rf MonaLisa.v${VERS}

cd Service

JH=""
if [ "$CLEAN" == "true" ]; then
    while [ ! -f "$JH/bin/java" ]; do
	if [ -z "$JH" ]; then
	    JH="$JAVA_HOME"
	fi
	if [ -z "$JH" ]; then
	    J=`which java`
	    if [ ! -z "$J" ]; then	
		J=`dirname "$J"`
		JH=`dirname "$J"`
	    fi
	fi
	echo -n "Path to the java home [$JH]: "
	read JT
	if [ ! -z "$JT" ]; then
	    JH="$JT"
	fi
    done
    
    echo -n "Please specify the farm name [`hostname -s`]: "
    read FARMNAME
    if [ -z "$FARMNAME" ]; then
	FARMNAME=`hostname -s`
    fi

    cat CMD/ml_env | \
	myreplace "/usr/local/java" "$JH" | \
	myreplace "\${HOME}/MonaLisa.v${VERS}" "$INSTALL" | \
        myreplace "/Service/TEST" "/Service/${DIST}" | \
	myreplace "/test.conf" "/vdtFarm.conf" | \
	myreplace "#MONALISA_USER=\"monalisa\"" "MONALISA_USER=$1" | \
	myreplace "#FARM_NAME=\"\"" "FARM_NAME=\"$FARMNAME\"" \
    > CMD/ml_env.newconfig
    rm -f CMD/ml_env
    mv CMD/ml_env.newconfig CMD/ml_env
    
    cat CMD/MLD | \
	myreplace "/home/monalisa/MonaLisa.v${VERS}" "$INSTALL" | \
	myreplace "=monalisa" "=$1" \
    > CMD/MLD.new
    rm -f CMD/MLD
    mv CMD/MLD.new CMD/MLD
    chmod a+x CMD/MLD
fi

if [ -d "${DIST}" ]; then 
    exit
fi

OK="n"
U=$1
E="$1@`hostname -f`"
CITY=""
COUNTRY=""
LAT="0"
LONG="0"

while [ ! "$OK" == "y" ]; do
    echo -n "Contact name [$U]: "
    read T
    if [ ! -z "$T" ]; then
	U=$T
    fi

    echo -n "Contact email [$E]: "
    read T
    if [ ! -z "$T" ]; then
	E=$T
    fi
    
    echo -n "City [$CITY]: "
    read T
    if [ ! -z "$T" ]; then
	CITY=$T
    fi
    
    echo -n "Country [$COUNTRY]: "
    read T
    if [ ! -z "$T" ]; then
	COUNTRY=$T
    fi

    echo -n "Location LAT (-180..180) [$LAT]: "
    read T
    if [ ! -z "$T" ]; then
	LAT=$T
    fi
    
    echo -n "Location LONG (-180..180) [$LONG]: "
    read T
    if [ ! -z "$T" ]; then
	LONG=$T
    fi

    echo -n "Is this information correct (y/n)? [n]: "
    read OK
done

mkdir ${DIST}

cat >$DIST/vdtFarm.conf <<EOF
#
# Monitoring the Mater node 
# The /proc files are used to collect data
# 
*Master
>localhost
monProcLoad%30
monProcStat%30
monProcIO%30
#
# Get the monitoring information for the farm 
# nodes using Ganglia.
# if the ganglia master demon is running on a 
# different system that this one (where MonALISA 
# is running) please change the "localhost"  with 
# the appropriate system . If Ganglia demon is 
# running on a different port that the default one
# please also change the port no.
#
*PN_vdt{monIGangliaTCP, localhost, 8649}%30
# 
# In case this master node is in the multi-cast 
# range of the ganglia "sensors" running on the 
# farm nodes, you may use the ganglia multi-cast 
# collection module  to get the information from 
# the ganglia monitoring system. In this case 
# please uncomment the next line and comment 
# the one which is using TCP.  
# 
#
#*PN_vdt{monMcastGanglia, localhost, "GangliaMcastAddress=229.2.11.71; GangliaMcastPort=8649"} 
#
#

#
# In case you are running SNMP on farm nodes you
# can use MonALISA snmp modules
#
#*PN
#>node1.domainname
#snmp_Load%30
#snmp_CPU%30
#snmp_IO%30
#>node2.domainname
#snmp_Load%30
#snmp_CPU%30
#snmp_IO%30
#
#
#

# the ABping module 
*ABPing{monABPing, localhost, " "}
#
# the VO Jobs module 
# these  modules require the VDT environment to run correctly
#
# *VO_JOBS{monVoJobs,localhost,SDSS,ATLAS,iVDgL,USCMS,LIGO,BTeV}%300
#
# the VO io module 
#
# *VO_IO{monVOgsiftpIO, localhost, }%300
#
EOF

cat >$DIST/ml.properties <<EOF
#################################################################
# This file contains the parameters for a MonALISA service
#
######################General####################################
##
## Please fill these parameters with your Name and Email Address
## in order to let us know whom to contact if we have  problems
## running MonALISA.
#
MonaLisa.ContactName=user
MonaLisa.ContactEmail=email

## Send change notifications to the above email address
## Set this to true if you want to receive emails about changes
#include.MonaLisa.ContactEmail=false

#
## The folwing parameters will be used to place your farm
## on the WoldMap in the GUI Client
#
#
MonaLisa.Location=CITY
MonaLisa.Country=COUNTRY
#
## The Latitude and the Longitude are defined by the:
#
MonaLisa.LAT=LATITUDE
MonaLisa.LONG=LONGITUDE
#
#
######################## REGISTRATION #############################
# List of Lookup Services separated by comma
#
lia.Monitor.LUSs=monalisa.cern.ch
#
# Group to use in Jini! The LUSs define above must have been started
# using this group. For example to set group to cms-us
# lia.Monitor.group=cms-us
#
lia.Monitor.group=grid3
#
#
## In case you have 2 NICs card or the system configuration
## provides MonALISA with a wrong IP address ( like 127.0.0.1,
## or the internal network address) this option allows you
## to force the MonALISA service to declare the corect IP
## lia.Monitor.useIPaddress=<external_IP_address>
#lia.Monitor.useIPaddress=x.x.x.x
#
#
######################## PORTS to be used ##############################
#
# This will allow to start the service according to the firewall policy
# TCP & UDP  ports used by MonaLisa.
#
#
# MonaLisa will try to bind a few ports ( actually 3 ) in a range between
# lia.Monitor.MIN_BIND_PORT and lia.Monitor.MAX_BIND_PORT.
#
#Default
#lia.Monitor.MIN_BIND_PORT=9000
#lia.Monitor.MAX_BIND_PORT=9010
#
#
########################## Loading of Additional modules ####################
#
# If you have your own modules  or wnat to use modules from usr_code
# uncomment the folowing option and fill in the correct path
# lia.Monitor.CLASSURLs=file:/<YOUR FULL PATH TO MonaLisa_HOME>/Service/usr_code/PBS/
# or
# You have the path to MonaLisa in ${MonaLisa_HOME}
# If you add your own Module ( e.g MyModule in usr_code/MyModule ) you can set
# the folowing parameter as it follows
#
# lia.Monitor.CLASSURLs=file:${MonaLisa_HOME}/Service/usr_code/MyModule/
# In case of a jar file, it must be included in the CLASSURLS
# ATTENTION about the trailing /  for directories  !!!!!!!!!!!!
#
lia.Monitor.CLASSURLs=file:${MonaLisa_HOME}/Service/usr_code/PBS/
#
# Multiple places can be specified separated by ,
#
########################## SNMP ############################################
# If you want to use the internal SNMP modules general parameters can
# be set with the following options
# if you want a different community than public to intergate your nodes
# default the public community is used
#
#lia.Monitor.SNMP_community=public
#
#
# Port for SNMP queries
# Default is 161
#lia.Monitor.SNMP_port=1611

#########################################################################
## Wheter to use SNMP or not for MonaLisa to monitor itself
## it's better to be left as it is. If this parameter is set to true you
## should have SNMP running on the node that MonaLisa is started. For the self
## monitoring part we parse /proc files if this option is false.
## Default is false
#lia.monitor.Farm.use_SNMP=false

################## Web Services Settings ############################
##
## Wheter to start or not the Axis WebServer ( default false )
##
#lia.Monitor.startWSDL=false

##
##Port used for the WebService. Default is 6004
#lia.Monitor.wsdl_port=6004

##
## The URL for MLWebService will be
## http://<your_hostname>:<wsdl_port>/axis/services/MLWebService
##
## E.g: To get the WSDL for MLWebService from localhost you can try
## $wget http://localhost:6004/axis/services/MLWebService?wsdl
##

################ Store configuration ####################################
##
## There are three possible configurations.
## There are two embedded DB ( MySQL and McKOI )
##
## MonALISA will start by default using embedded DB for storing monitoring
## informations
##
## This is suitable for small sites. To keep a longer history, or run on a large
## cluster we recommend using a standalone MySQL with a larger cache size,
## instead of McKoi or embedded MySQL.
##
## To store the informations in MySQL please comment the line 
## lia.Monitor.jdbcDriverString=com.mckoi.JDBCDriver and uncomment
## lia.Monitor.jdbcDriverString = com.mysql.jdbc.Driver
##
##
#lia.Monitor.jdbcDriverString=com.mckoi.JDBCDriver

#
## MySQL Embedded
##
## The flag lia.Monitor.use_emysqldb=true is the only one needed to start MonALISA
## with embedded MySQL
##
lia.Monitor.use_emysqldb=true

##
## McKOI
##
## If you would like MonALISA to start with embedded McKOI please comment the line
## lia.Monitor.use_emysqldb=true or set the flag to false
##
lia.Monitor.jdbcDriverString=com.mckoi.JDBCDriver

##
## How long to keep the data. Default ( 3 h = 10800 s)
##
#lia.Monitor.keep_history=10800

lia.Monitor.keep_history=10800

## The data collected from the monitoring modules can be stored in several
## tables, to allow users to interrogate it from their clients.
##
## These tables are defined below with the following parameters:
##
##    lia.Monitor.Store.TransparentStoreFast.web_writes = <nr_of_tables>
##
## For each of the <nr_of_tables> there are given several parameters:
##
##    lia.Monitor.Store.TransparentStoreFast.writer_N.total_time=<time in seconds>
##    lia.Monitor.Store.TransparentStoreFast.writer_N.samples=<number of values to be stored on this interval>
##    lia.Monitor.Store.TransparentStoreFast.writer_N.table_name=<table name, should be unique>
##    lia.Monitor.Store.TransparentStoreFast.writer_N.writemode=<write mode>
##
## where N is a number from 0 to <nr_of_tables>-1, and the parameters represent:
##
##  ..table_name : is the name of this table. This name should be unique.
##
##  ..writemode  : represents the way this table is used. There are 3 possibilities:
##             0 - write average values to this table, one for each total_time/samples
##                 seconds. The table will be used to store "double" values.
##             1 - write every value received, do not mediate the data. This also
##                 stores "double" values.
##             2 - the table will store any kind of data ( e.g. Strings ). The objects will only be stored
##                 since there is no way to know at this level how to compute the average
##                 for them.
##
##  ..total_time : is the interval, in SECONDS, for which this table stores data. Values
##                 older than this are removed from the table.
##
##  ..samples    : is the number of values to be stored in the table, on the specified
##                 interval. This parameter is only used if writemode is set to 0 (writemode=0),
##		   i.e. for having a fixed number of samples for averaged data. When write mode is 1 or 2
##                 the "samples" value is ignored, all the data that is received being
##                 stored into the database.
##
##  IMPORTANT: The tables must be defined from the one with smallest total_time to the
##             biggest total_time.
##
##
## IMPORTANT: The following values are ignored if lia.Monitor.jdbcDriverString=com.mckoi.JDBCDriver or
## if lia.Monitor.jdbcDriverString is not specified because MonALISA will use the embedded database and
## the only parameter that is considered is lia.Monitor.keep_history
##

## To save data into MySQL db just uncomment and fill the next lines
## with the right values.
##

#lia.Monitor.jdbcDriverString = com.mysql.jdbc.Driver
#lia.Monitor.ServerName = 127.0.0.1
#lia.Monitor.DatabaseName = mon_data
#lia.Monitor.UserName = mon_user
#lia.Monitor.Pass = mon_pass
#lia.Monitor.DatabasePort = 3306

## how many tables are defined
lia.Monitor.Store.TransparentStoreFast.web_writes = 3

lia.Monitor.Store.TransparentStoreFast.writer_0.total_time=10800
lia.Monitor.Store.TransparentStoreFast.writer_0.table_name=monitor_s_3hour
lia.Monitor.Store.TransparentStoreFast.writer_0.writemode=1

lia.Monitor.Store.TransparentStoreFast.writer_1.total_time=10800
lia.Monitor.Store.TransparentStoreFast.writer_1.table_name=monitor_s_e_3hour
lia.Monitor.Store.TransparentStoreFast.writer_1.writemode=2

lia.Monitor.Store.TransparentStoreFast.writer_2.total_time=36000
lia.Monitor.Store.TransparentStoreFast.writer_2.samples=60
lia.Monitor.Store.TransparentStoreFast.writer_2.table_name=monitor_s_10hour


############################## ABPing Configuration URL ####################
## ABPing is used to measure the connectivity between different farms, using
## small UDP packets. This is a global configuration file for all farms. If
## you want to see the connectivity between yor farm and others in the GUI
## Client please inform us and we'll add the peers in this config file.
## So, for now, please do not modify this parameter
lia.Monitor.ABPing.ConfigURL=http://monalisa.cern.ch/ABPingFarmConfig


################ Logging configuration ##################################
##
## How much logging info
## MIN is .level = OFF
## MAX is .level = ALL
##
## Other values for this parameter can be: SEVERE, WARNING, CONFIG, INFO, FINE, FINER, FINEST
## Please notice that the last two options are used only for debugging and generates large
## output!
##
##
## this option is better to be left as it is. Please notice the dot before level .
.level = OFF
lia.level = INFO


################ Advanced logging ( 'logrotate' style )###########################
##
## If you wold like to enable MonALISA to "logrotate" it's logs
## please comment the upper 3 lines and uncomment the following ones
##
## This will create 4 files that will be logrotated, after reaching
## the size limit
##
handlers= java.util.logging.FileHandler
java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter

# File size in bytes!
java.util.logging.FileHandler.limit = 1000000

#Number of files used in cycle through
java.util.logging.FileHandler.count = 4

#Whether should append at the end of a file log or start with a new one
java.util.logging.FileHandler.append = true
java.util.logging.FileHandler.pattern = ML%g.log

## logging to stdout and stderr options
## MonaLisa uses standard logging included since java 1.4
# handlers= java.util.logging.ConsoleHandler
# java.util.logging.ConsoleHandler.level = FINEST
# java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter
EOF

cat $DIST/ml.properties | \
    myreplace "=user" "=$U" | \
    myreplace "=email" "=$E" | \
    myreplace "CITY" "$CITY" | \
    myreplace "COUNTRY" "$COUNTRY" | \
    myreplace "LATITUDE" "$LAT" | \
    myreplace "LONGITUDE" "$LONG " \
> $DIST/ml.properties.new
rm -f $DIST/ml.properties
mv $DIST/ml.properties.new $DIST/ml.properties

cat >$DIST/db.conf.embedded <<EOF

#######################################################
#
# Configuration options for the Mckoi SQL Database.
#
# NOTE: Lines starting with '#' are comments.
#
#######################################################

#
# database_path - The path where the database data files
#   are located.
#   See the 'root_path' configuration property for the
#   details of how the engine resolves this to an
#   absolute path in your file system.

database_path=MKDB/data

#
# log_path - The path the log files are written.
#   See the 'root_path' configuration property for the
#   details of how the engine resolves this to an
#   absolute path in your file system.
#   The log path must point to a writable directory.  If
#   no log files are to be kept, then comment out (or
#   remove) the 'log_path' variable.

log_path=MKDB/log

#
# root_path - If this is set to 'jvm' then the root
#   path of all database files is the root path of the
#   JVM (Java virtual machine) running the database
#   engine.  If this property is set to 'configuration'
#   or if it is not present then the root path is the
#   path of this configuration file.
#   This property is useful if you are deploying a
#   database and need this configuration file to be the
#   root of the directory tree of the database files.

root_path=configuration
#root_path=jvm

#
# jdbc_server_port - The TCP/IP port on this host where
#   the database server is mounted.  The default port
#   of the Mckoi SQL Database server is '9157'

jdbc_server_port=9157

#
# ignore_case_for_identifiers - If enabled all
#   identifiers are compared case insensitive.  If
#   disabled (the default) the case of the identifier
#   is important.
#   For example, if a table called 'MyTable' contains
#   a column called 'my_column' and this property is
#   enabled, the identifier 'MYTAble.MY_COlumN' will
#   correctly reference the column of the table.  If
#   this property is disabled a not found error is
#   generated.
#   This property is intended for compatibility with
#   other database managements systems where the case
#   of identifiers is not important.

ignore_case_for_identifiers=disabled

#
# socket_polling_frequency - Mckoi SQL maintains a pool
#   of connections on the server to manage dispatching
#   of commands to worker threads.  All connections on
#   the jdbc port are polled frequently, and ping
#   requests are sent to determine if the TCP
#   connection has closed or not.  This value determines
#   how frequently the connections are polled via the
#   'available' method.
#   The value is the number of milliseconds between each
#   poll of the 'available' method of the connections
#   input socket stream.  Different Java implementations
#   will undoubtedly require this value to be tweaked.
#   A value of '3' works great on the Sun NT Java 1.2.2
#   and 1.3 Java runtimes.
#
#   NOTE: This 'socket polling' module is a horrible hack
#   and will be removed at some point when the threading
#   performance improves or there is an API for non-
#   blocking IO.  I'll probably write an alternative
#   version for use with the improved Java version.

socket_polling_frequency=3




# ----- PLUG-INS -----

#
# database_services - The services (as a Java class) that
#   are registered at database boot time.  Each service
#   class is separated by a semi-colon (;) character.
#   A database service must extend
#   com.mckoi.database.ServerService
#
#database_services=mypackage.MyService

#
# function_factories - Registers one or more FunctionFactory
#   classes with the database at boot time.  A
#   FunctionFactory allows user-defined functions to be
#   incorporated into the SQL language.  Each factory class
#   is separated by a semi-colon (;) character.
#
#function_factories=mypackage.MyFunctionFactory

#
# The Java regular expression library to use.  Currently
# the engine supports the Apache Jakarta regular expression
# library, and the GNU LGPL regular expression library.
# These two regular expression libraries can be found at the
# following web sites:
#
# GNU Regexp: http://www.cacas.org/~wes/java/
# Apache Regexp: http://jakarta.apache.org/regexp/
#
# The libraries provide similar functionality, however they
# are released under a different license.  The GNU library
# is released under the LGPL and is compatible with GPL
# distributions of the database.  The Apache Jakarta library
# is released under the Apache Software License and must not
# be linked into GPL distributions.
#
# Use 'regex_library=gnu.regexp' to use the GNU library, or
# 'regex_library=org.apache.regexp' to use the Apache
# library.
#
# NOTE: To use either library, you must include the
#  respective .jar package in the Java classpath.

regex_library=gnu.regexp




# ----- PERFORMANCE -----

#
# data_cache_size - The maximum amount of memory (in bytes)
#   to allow the memory cache to grow to.  If this is set
#   to a value < 4096 then the internal cache is disabled.
#   It is recommended that a database server should provide
#   a cache of 4 Megabytes (4194304).  A stand alone
#   database need not have such a large cache.

data_cache_size=8192

#
# max_cache_entry_size - The maximum size of an element
#   in the data cache.  This is available for tuning
#   reasons and the value here is dependant on the type
#   of data being stored.  If your data has more larger
#   fields that would benefit from being stored in the
#   cache then increase this value from its default of
#   8192 (8k).

max_cache_entry_size=8192

#
# lookup_comparison_list - When this is set to 'enabled'
#   the database attempts to optimize sorting by generating
#   an internal lookup table that enables the database to
#   quickly calculate the order of a column without having
#   to look at the data directly.  The column lookup
#   tables are only generated under certain query
#   conditions.  Set this to 'disabled' if the memory
#   resources are slim.

lookup_comparison_list=enabled

#
# lookup_comparison_cache_size - The maximum amount of
#   memory (in bytes) to allow for column lookup tables.
#   If the maximum amount of memory is reached, the lookup
#   table is either cached to disk so that is may be
#   reloaded later if necessary, or removed from memory
#   entirely.  The decision is based on how long ago the
#   table was last used.
#
#   This property only makes sense if the
#   'lookup_comparison_list' property is enabled.
#
# NOTE: This property does nothing yet...

lookup_comparison_cache_size=2097152

#
# index_cache_size - The maximum amount of memory (in
#   bytes) to allow for the storage of column indices.
#   If the number of column indices in memory reaches
#   this memory threshold then the index blocks are
#   cached to disk.
#
# ISSUE: This is really an implementation of internal
#   memory page caching but in Java.  Is it necessary?
#   Why not let the OS handle it with its page file?
#
# NOTE: This property does nothing yet...

index_cache_size=2097152

#
# max_worker_threads - The maximum number of worker
#   threads that can be spawned to handle incoming
#   requests.  The higher this number, the more
#   'multi-threaded' the database becomes.  The
#   default setting is '4'.

maximum_worker_threads=4

#
# soft_index_storage - If this is set to 'enabled', the
#   database engine will keep all column indices behind a
#   soft reference.  This enables the JVM garbage collector
#   to reclaim memory used by the indexing system if the
#   memory is needed.
#
#   This is useful for an embedded database where requests
#   are rare.  When the database part is idle, the index 
#   memory (that can take up significant space for large
#   tables) is reclaimed for other uses.  For a dedicated
#   database server it is recommended this is disabled.
#
#   Enable this if you need the engine to use less memory.
#   I would recommend the config property
#   'lookup_comparison_list' is disabled if this is enabled.
#   The default setting is 'disabled'.

soft_index_storage=disabled

#
# dont_synch_filesystem - If this is enabled, the engine
#   will not synchronize the file handle when a table change
#   is committed.  This will mean the data is not as
#   safe but the 'commit' command will work faster.  If this
#   is enabled, there is a chance that committed changes will
#   not get a chance to flush to the file system if the
#   system crashes.
#
#   It is recommended this property is left commented out.
#
dont_synch_filesystem=enabled

#
# transaction_error_on_dirty_select - If this is disabled
#   the 4th conflict (dirty read on modified table) will
#   not be detected.  This has transactional consequences
#   that will cause data modifications to sometimes be
#   out of syncronization.  For example, one transaction
#   adds an entry, and another concurrent transaction
#   deletes all entries.  If this is disabled this
#   conflict will not be detected.  The table will end up
#   with the one entry added after commit.
#
#   It is recommended this property is left commented out.
#
#transaction_error_on_dirty_select=disabled








# ----- SPECIAL -----

#
# read_only - If this is set to 'enabled' then the database
#   is readable and not writable.  You may boot a database
#   in read only mode from multiple VM's.  If the database
#   data files are stored on a read only medium such as a
#   CD, then the property must be enabled else it will not
#   be possible to boot the database.
#   ( Uncomment the line below for read only mode )
#read_only=enabled




# ----- DEBUGGING -----

#
# debug_log_file - The file that is used to log all debug
#   information.  This file is stored in the 'log_path'
#   path.

debug_log_file=debug.log

#
# debug_level - The minimum debug level of messages that
#   are written to the log file.  Reducing this number
#   will cause more debug information to be written to
#   the log.
#     10 = INFORMATION
#     20 = WARNINGS
#     30 = ALERTS
#     40 = ERRORS

debug_level=20

#
# table_lock_check - If this is enabled, every time a
#   table is accessed a check is performed to ensure that
#   the table owns the correct locks.  If a lock assertion
#   fails then an error is generated in the log file.
#   This should not be enabled in a production system
#   because the lock assertion check is expensive. However
#   it should be used during testing because it helps to
#   ensure locks are being made correctly.

#table_lock_check=enabled
table_lock_check=disabled
EOF

echo ""
echo "The installation is complete now."
echo ""
echo "To change the configuration at a later time please see:"
echo "  $INSTALL/Service/CMD/ml_env"
echo "  $INSTALL/Service/CMD/site_env"
echo "  $INSTALL/Service/VDTFarm/ml.properties"
echo "  $INSTALL/Service/VDTFarm/vdtFarm.conf"
echo ""
echo "You can now start the MonALISA service by running :"
echo "  $INSTALL/Service/CMD/ML_SER start"
echo ""
echo "To start MonALISA (on RedHat) as a service you can: "
echo "  #cp $INSTALL/Service/CMD/MLD /etc/rc.d/init.d"
echo "  #chkconfig --add MLD"
echo "  #chkconfig --level 345 MLD on"
echo ""
echo "Thank you,"
echo "developers@monalisa.cern.ch"
echo ""
