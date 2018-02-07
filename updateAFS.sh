#!/bin/bash

scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar aliprod@lxplus:/afs/cern.ch/alice/alien2/.i386_linux24/alien/java/MonaLisa/Service/lib
scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar aliprod@lxplus:/afs/cern.ch/alice/alien2/.amd64_linux24/alien/java/MonaLisa/Service/lib
scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar root@pcalienstorage:/opt/alien64/alien/java/MonaLisa/Service/lib/
