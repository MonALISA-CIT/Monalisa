#!/bin/bash

cd `dirname $0`

myreplace(){
    a=`echo $1 | sed 's/\//\\\\\//g'`
    b=`echo $2 | sed 's/\//\\\\\//g'`

    cat | sed "s/$a/$b/g"
}

export PGLIB=`pwd`/lib
export LD_LIBRARY_PATH=$PGLIB:$LD_LIBRARY_PATH
export PATH="`pwd`/bin:$PATH"

bin/initdb -D `pwd`/data -L `pwd`/share || exit

