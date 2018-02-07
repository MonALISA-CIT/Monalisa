#!/bin/sh

cd `dirname $0`

#WEBREP=http://monalisa.cern.ch/~repupdate
WEBREP=http://192.91.244.18/~repupdate

wget -q "$WEBREP/res_filelist.txt" -O res_filelist.txt || exit

for file in `cat res_filelist.txt`; do
    if [ -z "$file" ]; then
        continue
    fi

    dest="../../$file"

    dir=`dirname "$dest"`

    if [ ! -d "$dir" ]; then
        mkdir -p "$dir" 2>/dev/null
    fi

    echo -n "$file ... "

    if [ ! -z "$1" -a -f "$dest" ]; then
	echo "exists, skipping"
	continue
    fi
    
    if wget -q "$WEBREP/WEBS/WEB_GRAPH/$file" -O "$dest"; then
        echo "done"
    else
        echo "failed"
    fi
done

rm res_filelist.txt
