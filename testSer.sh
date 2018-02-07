#!/bin/bash

cd `dirname $0`

#scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar root@aliendb1:/opt/alien/java/MonaLisa/Service/lib
#scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar root@alimacx01:/opt/alien/java/MonaLisa/Service/lib

#TARGETS="aliprod@lxplus:/afs/cern.ch/alice/alien2/.i386_linux26/alien.v2-15/java/MonaLisa"
TARGETS="root@pcalienstorage:/opt/alien64/alien/java/MonaLisa root@pcalienstorage:/opt/alienOSX_10.5/alien/java/MonaLisa"
#TARGETS="aliprod@lxplus:/afs/cern.ch/alice/alien2/.i386_linux26/alien.v2-15_new_new/java/MonaLisa"

for TARGET in $TARGETS; do
    scp \
	/home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar \
	$TARGET/Service/lib
done
