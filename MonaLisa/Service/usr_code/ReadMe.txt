These are  examples of dynamicaly loadable modules 
which can be used   to store or send the the collected 
values to other applications.

The same mechanismis used to add new monitoring modules.

To load such modules into the simulation frmework the 
set  lia.Monitor.CLASSURLs=file:${lia.Monitor.HOME}/Service/usr_code/<YourModule(s)Dir>
in ml.properties under your farm directory to point to the 
directories (URLs) from where you want the code to be loaded. 
It can be a list of places separated by ","

To compile the code, you need FarmMonitor.jar in the classpath.

Example
=======

1) Go to <your_path_to_monalisa>/Service/CMD
2) Set the environment
3) Set the CLASSPATH environment


$cd ${HOME}/MonaLisa
$. ./ml_env
$CLASSPATH=.:$MonaLisa_HOME/Service/lib/FarmMonitor.jar

You should be able now to compile and run your own modules.
