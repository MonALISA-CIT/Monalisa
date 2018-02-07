#!/bin/sh

#
# DESCRIPTION
#   Copies a SSH public key from local system to the $HOME/.ssh/authorized_keys
#   on remote system. If the identity public key does not exist this script tries to generate a fresh key-pair.
#
# SYNTAX:
#   cpkey.sh <rem_host> [ <rem_acct> <identity> ]
#
# Authors: 
# Vlad Grama [vgrama@gmail.com]
# Adrian Muraru [adim@rogrid.pub.ro]


# 
# Defaults for optional args
#

IDENTITY="$HOME/.ssh/id_rsa"
REM_ACCT="$USER"

#
# exit with error
#
exiterr() {
  echo $1 >&2
  exit $2
}


#
# Main
#
[ $# -lt "1" ] && 
    exiterr "Syntax: $0 <rem_host> [ <rem_acct>  <identity> ]" 1
REM_HOST="$1"
[ $# -ge "2" ] && REM_ACCT="$2"
[ $# -ge "3" ] && IDENTITY="$3"


[ -f "${IDENTITY}.pub" ] || 
    (echo "Creating key pair..."; ssh-keygen -t rsa -b 1536 -C "MonALISA User" -N "" -f "$IDENTITY") || 
	exiterr "Failed to create ${IDENTITY} key pair. Exiting..." 1

#
# Copy public key file and also set strong use-options for it
#
echo "Appending pulic-key [ ${IDENTITY}.pub] to ${REM_HOST}:~${REM_ACCT}/.ssh/authorized_keys..."
echo "no-port-forwarding,no-X11-forwarding $(cat ${IDENTITY}.pub)" |
ssh "$REM_ACCT"@"$REM_HOST" "
   [ -d \$HOME/.ssh ] || { mkdir \$HOME/.ssh; chmod 700 \$HOME/.ssh; } 
   cat - >> \$HOME/.ssh/authorized_keys;
   chmod 644 \$HOME/.ssh/authorized_keys"
echo "Done."

exit 0
  

