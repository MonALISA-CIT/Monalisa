
This directory contains a simple module :
 
LSFJobs    ---- to get LSF jobs information

It can be customized for site specific data 

the comp script  compiles the module 

the test_LFSjobs is a simple way to test the module 
using the main class before using it in MonALISA.



To use the module in MonALISA please add this lines 
into the site config file :

*JOBS{LSFJobs,localhost,user_arguments}%60

and make sure that the path to this directory is set 
in your site ml.properties :

lia.Monitor.CLASSURLs=file:${MonaLisa_HOME}/Service/usr_code/LSFModules/

This will define a functional name ( cluster) called JOBS 
and will run LSFJobs module to collect data.
The user may pass additional parameters to the module 
for filtering the jobs 

In this example the module will be executed by MonALISA every 60s 
 
