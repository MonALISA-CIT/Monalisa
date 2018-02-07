#!/bin/sh

cd `dirname $0`

if [ "`id -u`" -eq 0 ]; then
    exec su - `cat conf/env.ACCOUNT` -c "`pwd`/`basename $0`"
fi

if [ ! "`id -u -n`" == "`cat conf/env.ACCOUNT`" ]; then
    echo ""
    echo "This is not the account from which the MonaLisa Repository was installed!"
    echo "Please su - `cat conf/env.ACCOUNT` before runing this script."
    echo ""
    exit 1
fi

scripts/testrun.sh || exit

cd `dirname $0`/lib

wget -q http://monalisa.cern.ch/~repupdate/update.sh -O update.sh
chmod a+x update.sh

./update.sh

rm update.sh

echo "Your jar files are now updated."
echo "You have to restart your repository for the changes to take effect."

rm ../scripts/TESTRUN
