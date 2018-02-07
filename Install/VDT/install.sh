#!/bin/bash

ACCOUNT=""
HOMEDIR=""

cd `dirname $0`

if [ `id -u` -ne 0 ]; then
    ACCOUNT=`id -u -n`
    pushd . &>/dev/null
    cd ~
    HOMEDIR=`pwd`
    popd &>/dev/null
    
    `dirname $0`/install2.sh $ACCOUNT $HOMEDIR
else
    echo "You cannot run MonALISA as root."
    echo -n "Please specify an account for the MonALISA service [monalisa]: "
    read ACCOUNT
    if [ -z "$ACCOUNT" ]; then
	ACCOUNT="monalisa"
    fi
    if [ "`id -u $ACCOUNT 2>/dev/null`" == "0" ]; then
	echo "You have specified a root account, the install will exit now."
	exit
    fi

    if [ -z "`id -u $ACCOUNT 2>/dev/null`" ]; then
	echo "The specified account does not exist, it will be created now"
	echo -n "${ACCOUNT}'s home dir [/home/$ACCOUNT]: "
	read HOMEDIR
	if [ -z "$HOMEDIR" ]; then
	    HOMEDIR="/home/$ACCOUNT"
	fi
	
	useradd -m -d "$HOMEDIR" "$ACCOUNT"
	
	if [ -z "`id -u $ACCOUNT 2>/dev/null`" ]; then
	    echo "There was a problem creating the specified account"
	    echo "The install will exit now."
	    exit
	fi
    else
	HOMEDIR=`finger "$ACCOUNT" | grep -E -e "^Directory:" | tr -s "\011" " " | cut "-d " -f2`
	
	while [ ! -d "$HOMEDIR" ]; do
	    echo "I cannot determine the ${ACCOUNT}'s home dir"
	    echo -n "Please specify the home dir [$HOMEDIR]: "
	    read HOMEDIR
	done
    fi
    
    mkdir -p $HOMEDIR/monalisa_install
    cp MonaLisa.v1.1.tar.gz $HOMEDIR/monalisa_install
    cp install2.sh $HOMEDIR/monalisa_install
    chown -R $ACCOUNT $HOMEDIR/monalisa_install
    
    su - $ACCOUNT -c "/bin/bash --login -c \"$HOMEDIR/monalisa_install/install2.sh $ACCOUNT $HOMEDIR\""
    
    rm -rf $HOMEDIR/monalisa_install &>/dev/null
fi
