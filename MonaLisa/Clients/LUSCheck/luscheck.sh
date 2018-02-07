#!/bin/bash

JAVA_HOME=/usr/local/java
PATH=$PATH:$JAVA_HOME/bin

MYDIR=`dirname $0`
MYDIR=`(cd $MYDIR ; pwd)`
PIDFILE=$MYDIR/LUSCheck.pid

OLDPID=`cat $PIDFILE 2>/dev/null`

kill -s 0 $OLDPID 2>/dev/null
IS_RUNNING=$?

case $1 in
	stop)
		if [[ "$IS_RUNNING" == "1" ]] ; then
			echo -n "Not running."
		else
			echo -n "Killing LUSCheck with pid $OLDPID.."
			kill $OLDPID 2>/dev/null && sleep 1 && echo -n "."
			kill $OLDPID 2>/dev/null && sleep 1 && echo -n "."
			kill $OLDPID 2>/dev/null && sleep 1 && echo -n "."
			kill -9 $OLDPID 2>/dev/null && sleep 1 && echo -n "."
			kill -s 0 $OLDPID 2>/dev/null && echo "Won't die!!!" && exit 1
			echo -n " Killed."
		fi
		echo
		echo "stop" > $PIDFILE
		;;
	start)
		[[ "$IS_RUNNING" == "0" ]] && echo "Already running with pid $OLDPID !" && exit
		echo -n "Starting LUSCheck... "
		
		JARS=""
		for f in $MYDIR/lib/*.jar ; do
			JARS="$JARS:$f"
		done
		(echo ; echo -n "--- Started on: " ; date ; echo) >> $MYDIR/LUSCheck.log
		$JAVA_HOME/bin/java \
			-jar \
			-cp $JARS \
			-Djava.security.policy=$MYDIR/policy.all \
			-Dlia.Monitor.ConfigURL=file:$MYDIR/app.properties \
			-Djava.util.logging.config.class=lia.Monitor.monitor.LoggerConfigClass \
			-Djava.util.logging.config.file=file:$MYDIR/app.properties \
			$MYDIR/lib/JLUSCheckClient.jar >>$MYDIR/LUSCheck.log 2>>$MYDIR/LUSCheck.log &
		echo $! > $PIDFILE
		sleep 1
		kill -s 0 $! 2>/dev/null
		if [[ "$?" == "0" ]] ; then
			echo "Started with pid $!"
		else
			echo "Failed to start! Check the logs."
		fi
		;;
	restart)
		$0 stop
		$0 start
		;;
	status)
		[[ "$OLDPID" == "stop" ]] && echo "Stopped. Won't start at '$0 check'." && exit
		[[ "$IS_RUNNING" == "1" ]] && echo "Not running." && exit
		echo "Running with pid $OLDPID"
		;;
	clean)
		rm -f $MYDIR/LUSCheck*.log
		;;
	check)
		[[ "$OLDPID" == "stop" ]] && exit
		if [[ "$IS_RUNNING" == "1" ]] ; then
			echo -n "Not running... "
			$0 start
		fi
		;;
	*)
		cat <<EOT
$0 - LUS Checker
Usage:
    $0 [start|stop|restart|status|clean|check]
    	- use 'check' when running from crontab
	- use 'clean' to remove all logs
	- if 'stop'-ped, it won't start at 'check'; you'll have to do 'start' first
EOT
		;;
esac

