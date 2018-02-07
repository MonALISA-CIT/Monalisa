#!/bin/bash

#scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/{FarmControl,FarmMonitor,JFarmMonitor}.jar root@pcalienstorage:/opt/alien32/alien//java/MonaLisa/Service/lib/
#scp -P 60000 /home/costing/workspace/MSRC/MonaLisa/Service/lib/*.jar /home/costing/workspace/MSRC/WEBS/WEB_GRAPH/WEB-INF/lib/lazyj.jar aliprod@alien.spacescience.ro:/software/alien/java/MonaLisa/Service/lib/
scp /home/costing/workspace/MSRC/MonaLisa/Service/lib/*.jar /home/costing/workspace/MSRC/WEBS/WEB_GRAPH/WEB-INF/lib/lazyj.jar costin@lfc.bitp.kiev.ua:MonaLisa/Service/lib/
