#!/bin/bash

(for i in `seq 1 10` ; do
	echo "Restarting LUS"
done)
#| mail -s "Problem with LUS @ $HOSTNAME" Catalin.Cirstoiu@cern.ch

