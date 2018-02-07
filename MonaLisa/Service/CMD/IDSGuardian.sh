#!/bin/sh

echo_usage() {
        echo
	echo "Usage: $0 [B|P] ip_address"
	echo "B - Block  <ip_address>"
	echo "P - un-block (Permit) <ip_address>"   
}
			
if [ $# -ne 2 ]; then
    echo_usage
    exit 1
fi
					
OPTION="$1"
IP="$2"
IPT=/sbin/iptables

##block
if [ "$OPTION" = "B" ]; then
    ## echo "$IP" >> blocked.list
    $IPT -A INPUT -s $IP -j DROP
##permit    
elif [ "$OPTION" = "P" ]; then 
    ##echo "$IP" >> un-blocked.list
    $IPT -D INPUT -s $IP -j DROP    
else  
    echo_usage
    exit 1
fi

exit 0
