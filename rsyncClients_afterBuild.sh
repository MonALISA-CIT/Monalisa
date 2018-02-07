#!/bin/bash

rsync -avuz WEBS/UPDATE/SLIBS_WCLIENTS/jClient/ ramiro@monalisa:UP_Client/jClient/
rsync -avuz WEBS/UPDATE/SLIBS_WCLIENTS/vrvsjClient/ ramiro@monalisa:UP_Client/vrvsjClient/
rsync -avuz resources/jnlp/ ramiro@monalisa:UP_Client/jnlp/
