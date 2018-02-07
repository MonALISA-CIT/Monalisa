#!/bin/bash

#   functions just like the "replace" utility, just that you can give regular expressions as 
# the string to be found and/or the string that will take it's place

cd `dirname $0`

(echo $*; cat) | awk -f replace.awk
