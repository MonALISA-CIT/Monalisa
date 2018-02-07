#!/bin/bash

#
# ML Script Version:1.9.0
#

# cmd_run.sh - Wrapper script to run commands from Java
#              Catches SIGTERM (sent on Process.destroy() from Java) and 
#              kills all spawned children. 
#              It needs #!/bin/bash to be able to run on Solaris.
#
# Comments and suggestions: support@monalisa.cern.ch
#
# 12/Oct/2010 - try a SIGTERM first on all children, and SIGKILL after that
# 17/Oct/2007 - fix for logging when /tmp/cmd_run.log is not writeable
# 23/Jul/2007 - better handling of signals; adding logging function
# 12/Jul/2007 - added the optional -t <timeout> parameter - run the command with a timeout
# 12/Jun/2006 - initial version

usage () {
	echo "Usage:"
	echo -e "\t$0 [-t <seconds>] command"
	echo -e "\t\t-t\trun the command with the specified timeout"
}

log () {
#	echo "LOG: cmd_run.sh [`date`] $*"
	true
}

kill_children () {
	parent=$1
	signal=$2
	
    if test -z "$parent" ; then log "kill_children called with empty parent! Ignoring." ; return ; fi
    if test -z "$signal" ; then log "kill_children called with no signal! Will use SIGKILL." ; signal=SIGKILL ; fi
	ps -A -eo "pid ppid" | ( read header	# ignore the dummy header
		while read a_pid a_ppid ; do
			procmap[$a_ppid]="${procmap[$a_ppid]} $a_pid"
		done
		children[0]=$parent
		i=0
		while test $i -lt ${#children[@]} ; do
			child=${children[$i]}
			log "sending $signal to $child ..."
			kill -$signal $child 			# kill each child
			for kid in ${procmap[$child]} ; do	# and add its kids to the list
				children[${#children[@]}]=$kid
			done
			i=`expr $i + 1`
		done
	)
}

term_kill_children() {
    for signal in "SIGTERM SIGKILL"; do 
        kill_children $1 $signal
        sleep 1 
    done
}

cleanup () {
	pid=$!
	log "Signal received in $$ Doing cleanup... for pid=$pid."
	term_kill_children $pid 2>/dev/null
    if test -n "$tpid" ; then term_kill_children $tpid 2>/dev/null ; fi
	echo KILLED
	echo KILLED >&2
	exit 7
}

wait_timeout () {
	sleep $timeout
	echo "TIMEOUT after $timeout seconds!"
	kill -USR1 $$
}


pid=""
tpid=""
trap cleanup TERM INT USR1

if test "$#" == "0" -o "$1" == "-h" -o "$1" == "--help" -o "$1" == "-help" ; then
	usage
	exit 0
fi

if test "$1" = "-t" ; then
	timeout=$2
	wait_timeout &
	tpid=$!
	shift 2
fi

cmd=$*

log "MyPID=$$ Running cmd '$cmd'..."
/bin/sh -c "$cmd" <&0 &
pid=$!
wait $pid
exit_code=$?
if test -n "$tpid" ; then kill_children $tpid 2>/dev/null ; fi
exit $exit_code

