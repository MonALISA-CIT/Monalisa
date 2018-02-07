##/usr/bin/env python

""" parseGatekeeper.py """


myProgram = 'parseGatekeeper.py'
###############################################
# Reads the GLOBUS gatekeeper logs files
# accumulating data on job submissions by VO.
###############################################

import sys, os, copy
from time  import *
import string
import getopt
import traceback
import types
import commands

import fileinput

##############################################################################
class Gatekeeper:
  #-- Gatekeeper log format ----------
  # eg - Sat Sep 13 03:04:35 2003
  GATEKEEPER_TIME_FORMAT = "%a %b %d %H:%M:%S %Y"
  #-- Default time format ------------
  DEFAULT_DATE_FORMAT = "%m/%d/%Y %H:%M:%S"
  ##############################################################################

  def __init__(self,debugFile=None,debug=[-1,-1],testlimit=0,mode="TOTALS"):
    myMethod = "__init__"
    # ---- debug attributes ---
    self.debugLevelScreen  = debug[0]
    if len(debug) == 1:
      self.debugLevelFile  = self.debugLevelScreen
    else:
      self.debugLevelFile  = debug[1]
    if debugFile == None:
      self.debugFile       = "%s.log" % myProgram
    else:
      self.debugFile       = debugFile

    if self.debugLevelFile < 0:
      self.debugFD   = None
    else:
      self.debugFD   = open(self.debugFile,"w")
      
    self.data = {}
    self.totals = {}
    self.voTable = { "uscms01"  : "CMS",
                     "usatlas1" : "ATLAS",
                     "sdss"     : "SDSS",
                     "lsc01"    : "LIGO",
                     "ivdgl"    : "iVDgL",
                     "btev"     : "BTeV",
                    }

    self.homedirs = {}
    self.findHomeDirectories()

    self.testlimit   = testlimit
    self.mode        = mode
    self.submissions = {}
    self.line        = ""
    self.setInterval(None,None)  # sets the parseInterval
    self.collectData = 0  # if TRUE, we collect data (based on intervals)
    self.pastEndTime = 0  # if TRUE, we are past the end time in the log file
     
    #-------------------------------
    self.log(myMethod,"Start: debug file(%s) debug file level(%s) debug screen level(%s>" % (self.debugFile,self.debugLevelFile,self.debugLevelScreen),3)
  
  #----------------
  def findHomeDirectories(self):
    users = self.voTable.keys()
    for user in users:
      if user == "":
        continue
      self.homedirs[user] = commands.getoutput("grep %s /etc/passwd | cut -d':' -f6" % user)   


  #----------------
  def getGS(self):
    return self.data

  #----------------
  def processGS(self):
    self.updateVO()
##    vo = self.data['vo']
    user = self.data['user']
    if user == "":
      return
    if self.totals.has_key(user):
       pass
    else:
       self.totals[user] = { 'submissions' :  0,
                            'failures'    :  0,
                            'pings'       :  0,
                            'failedjobs'  :  0,
                           }

    if self.isSubmission():
       self.totals[user]['submissions'] = self.totals[user]['submissions'] + 1
    if self.isFailure():
       self.totals[user]['failures']    = self.totals[user]['failures'] + 1
    if self.isPing():
       self.totals[user]['pings']       = self.totals[user]['pings'] + 1
    if self.homedirs.has_key(user):
       self.findFailedJob(user)
       
  #-------------------------------
  def findFailedJob(self,user):
    """ Find failed jobs.
        Requires finding the gram log files in the user's HOME directory.
    """
    myMethod = "findFailedJob"

    gramFile = "%s/gram_job_mgr_%s.log" % (self.homedirs[user],self.data['child']) 
    self.log(myMethod,"GRAM file: %s" % gramFile,1)
    if os.path.isfile(gramFile):
      self.totals[user]['failedjobs'] = self.totals[user]['failedjobs'] + 1
    
  #----------------
  def updateSubmission(self,pid,time):
    self.data = { 'pid'      : pid ,
                  'time'     : int(time),
                  'status'   : "",
                  'user'     : "",
                  'child'    : "",
                }

  #----------------
  def updateUser(self,user):
    self.data[ 'user' ] = user  

  #----------------
  def updateDN(self,dn):
    self.data[ 'dn' ] = dn  

  #----------------
  def updateVO(self):
    user = ""
    if self.data.has_key('user'):
      user = self.data['user']
    else:
      self.data[ 'vo' ] = 'UNKNOWN_NO_USR'

    if self.voTable.has_key(user):
      self.data[ 'vo' ] = self.voTable[user]   
    else:
      self.data[ 'vo' ] = 'UNKNOWN_VO'

    

  #----------------
  def updateChild(self,childPID):
    self.data[ 'child' ] = childPID   
    self.setStatusSuccess()

  #----------------
  def updateFailedSubmission(self,message):
    self.data[ 'message' ] = message  
    self.setStatusFailed()
    self.processGS()

  #----------------
  def setStatusFailed(self):
    self.data[ 'status'  ] = "FAILED"


  #----------------
  def setStatusSuccess(self):
    self.data[ 'status'  ] = "SUCCESS"  
    self.processGS()

  #----------------
  def updatePing(self):
    self.data['status' ] = "PING"
    self.processGS() 

  #----------------
  def vo(self):
    return self.data['vo' ]

  #----------------
  def isPing(self):
    if self.data['status' ] == "PING":
      return 1
    return 0

  #----------------
  def isFailure(self):
    if self.data['status'] == "FAILED": 
      return 1
    return 0

  #----------------
  def isSubmission(self):
    if (self.data['status'] == "FAILED") or (self.data['status'] == "SUCCESS"):
      return 1
    return 0
  
  #----------------
  def getTotals(self):
    return self.totals



    
  ################################################
  def log(self,method,msg,level=0):
    try:
      if ( level <= int(self.debugLevelFile) ) or (level <= int(self.debugLevelScreen)):
        raise ZeroDivisionError
    except ZeroDivisionError:
      f = sys.exc_info()[2].tb_frame.f_back
      (filename, linenum,methodname, line) = traceback.extract_stack(f,1)[0]
      datetime = strftime('%m/%d/%y %H:%M:%S',localtime(time()))
      message = "<%s %s %s> %s" % (datetime,methodname,linenum,msg)
##      message = "<%s %s %s %s> %s" % (datetime,filename,methodname,linenum,msg)
      if ( level <= int(self.debugLevelFile) ):
        self.debugFD.write("%s\n" % message)
      if (level <= int(self.debugLevelScreen)):
        print "%s" % message
    except:
       raise

  def closeLog(self):
     if self.debugFD == None:
       pass
     else:
       self.debugFD.close()


  ################################################
  def processLogFile(self,gatekeeperLog,startTime=None,endTime=None):
    myMethod = 'processLogFile'
    args = "gatekeeper log(%s)" % gatekeeperLog
    self.log(myMethod,"Start: %s" % (args),0)

    try:
      self.linecnt = 0
      for self.line in fileinput.input(gatekeeperLog):
        # --- see if we have past the end time in the log file --
        if self.pastEndTime:
          self.log(myMethod," We've reached the end time specified. Closing log file",0)
          break
        self.linecnt = self.linecnt + 1
        # ---  strip off the newline char ---
        self.line = self.line[0:len(self.line) - 1] 
        if self.line == "":
          continue
 

        if self.testlimit > 0:
          if self.linecnt > self.testlimit:
            self.log(myMethod,"TEST MODE: Terminated early at %s lines read" % (self.testlimit),0)
            break
##      --------- skip records we have already processed ------
##      if self.line[1:11] <> self.yesterday:
##        continue

        self.processLine()
      ## --- end of for loop ----

      fileinput.close()
      self.log(myMethod,"Records read from log file: %s" % (self.linecnt),0)

      totals =  self.getTotals()
    except:
      self.log(myMethod,"Unexpected exception: ",0)
      raise
    return totals

  ################################################
  def formatDate(self,time):
    myMethod = 'formatDate'
    return strftime(Gatekeeper.DEFAULT_DATE_FORMAT,localtime(time))

  ################################################
  def dateAsSeconds(self,time):
    myMethod = 'dateAsSeconds'
    return mktime(strptime(time,Gatekeeper.DEFAULT_DATE_FORMAT))


  ################################################
  def setInterval(self,startTime=None,endTime=None):
    myMethod = 'setInterval'
    args = "startTime(%s) endTime(%s)" % (startTime,endTime)
    self.log(myMethod,"Start: %s" % args,0)

    INTERVAL = 60 # --- default interval to look back to in minutes ---

    if endTime == None:
      self.endTime = time()
    else:
      self.endTime = self.dateAsSeconds(endTime)

    # Aug 21 13:38:14 2003 / 1061494694.0 
    if startTime == None:
      ## -- 1 hour is default period ---
      self.startTime   = self.endTime - (60 * INTERVAL) 
    else:
      self.startTime = self.dateAsSeconds(startTime)
    
    self.log(myMethod,"     start(%s / %s)" % (self.startTime,self.formatDate(self.startTime)),0)
    self.log(myMethod,"       end(%s / %s)" % (self.endTime,  self.formatDate(self.endTime)),0)
    
  ################################################
  def processLine(self):
    myMethod = 'processLine'
    self.log(myMethod,"Start",4)
    
    if self.findStart():
      return

    # --- check to see if we should be collecting data ---
    if self.collectData == 0:
      return

    # --- collect the additional data ---
    if self.findDN():
      return
    if self.findUser():
      return
    if self.findChild():
      return
    if self.findPingOnly():
      return
    if self.findFailedSubmission():
      return


  ################################################
  def findStart(self):
    """ Find the start of a grid submitted job.
        Capture the pid and time.
    """
    myMethod = 'findStart'
    #-- Strings indicating an attempted submission ---
    startString1 = 'Notice: 6: globus-gatekeeper pid='
    startString2 = ' starting at '

    #-- Search ---
    idx1 = int(string.find(self.line,startString1))
    idx2 = int(string.find(self.line,startString2))
    if (idx1 < 0) or (idx2 < 0):
      return 0

    #-- found one -----
    self.printline(4)
    pid  =  self.line[len(startString1) : idx2]

    strTime =  self.line[idx2 + len(startString2) : len(self.line)]
    ## ---- time in seconds ------
    time = mktime(strptime(strTime,Gatekeeper.GATEKEEPER_TIME_FORMAT))

    ## ---- check if records are within time interval being collected ------
    ## ---- this is the only reocrd in the log taht we can really do this --
    self.log(myMethod,"Comparison:  %s <= %s < %s)" % (self.startTime,time,self.endTime),4)
    self.collectData   = 0
    if (time <= self.startTime):
      return 1
    if (time > self.endTime):
      self.pastEndTime = 1
      return 1
    #-- found one to record -----
    self.printline(2)
    self.log(myMethod,"Comparison:  %s <= %s < %s)" % (self.startTime,time,self.endTime),2)
    self.collectData = 1
    
    self.log(myMethod,"Grid Submission: %s" % self.getGS(),3)
    self.log(myMethod,"TOTALS: %s" % self.getTotals(),3)
    self.log(myMethod,"#########################",3)
    self.log(myMethod,"pid(%s) time(%s / %s)" % (pid,strTime,time),3)
    self.updateSubmission(pid,time)
    return 0

  ################################################
  def findDN(self):
    """ Find the DN line to identify the VO.
    """
    myMethod = 'findDN'
    startString1 = 'Notice: 5: Authenticated globus user: '
    idx1 = int(string.find(self.line,startString1))
    if (idx1 < 0):
      return 0
    # -- found one ----
    self.printline(4)
    dn  =  self.line[len(startString1) : len(self.line)]
    self.log(myMethod,"DN(%s)" % (dn),3)
    self.updateDN(dn)
    return 1
    
  ################################################
  def findUser(self):
    """ Find the user line to identify the VO.
    """
    myMethod = 'findUser'
    startString1 = 'Notice: 5: Authorized as local user: '
    idx1 = int(string.find(self.line,startString1))
    if (idx1 < 0):
      return 0
    # -- found one ----
    self.printline(0)
    user  =  self.line[len(startString1) : len(self.line)]
    self.log(myMethod,"user(%s)" % (user),3)
    self.updateUser(user)
    return 1

  ################################################
  def findChild(self):
    """ Find the child line indicating a successul submission.
    """
    myMethod = 'findChild'
    startString1 = 'Notice: 0: Child '
    startString2 = ' started'
    idx1 = int(string.find(self.line,startString1))
    idx2 = int(string.find(self.line,startString2))
    if (idx1 < 0) or (idx2 < 0):
      return 0
    # -- found one ----
    self.printline(4)
    childPID  =  self.line[len(startString1) : idx2]
    # -- submission complete - turn off collection
    self.collectData   = 0

    self.log(myMethod,"childPID(%s)" % (childPID),3)
    self.updateChild(childPID)
    return 1


  ################################################
  def findPingOnly(self):
    """ Find ping only requests.
        These are not considered actual requests for service.
        They are not counted in the submission statistics.
        The log files generally show 2 of these back to back so one
        has to ignore the 2nd one.
    """
    myMethod = 'findPingOnly'
    startString1 = 'Failure: ping successful'
    idx1 = int(string.find(self.line,startString1))
    if (idx1 < 0): 
      return 0
    # -- found one ----
    self.printline(4)
    # -- submission complete - turn off collection
    self.collectData   = 0

    self.log(myMethod,"PING ONLY",3)
    self.updatePing()
    return 1

  ################################################
  def findFailedSubmission(self):
    """ Find failed submissions.
    """
    myMethod = 'findFailedSubmission'

    # -- more than one set of strings to look for -----
    startStrings = (' failed authorization. ',' GSS authentication failure')
    for startString1 in startStrings:
      idx1 = int(string.find(self.line,startString1))
      if (idx1 >= 0):
        # -- found one ----
        self.printline(4)
        # -- submission complete - turn off collection
        self.collectData   = 0
        self.log(myMethod,"failedSubmissions(%s)" % (startString1),3)
        self.updateFailedSubmission(startString1)
        return 1
    #-- did not find one ----
    return 0

  ################################################
  def printline(self,level):
    myMethod = 'printline'
    self.log(myMethod,"line(%s): %s" % (self.linecnt,self.line),level)
 

#####################################################################
##### MAIN ##########################################################
def parseArgs(args):
  myMethod = "parseArgs"

  args = { "start-date"  : "start-date=",
           "end-date"    : "end-date=",
           "debug"       : "debug=",
           "debug-file"  : "debug-file=",
           "logfile"     : "logfile=",
           "vo"          : "vo=",
           "mode"        : "mode=",
           "testlimit"   : "testlimit=",
         }
  #--------------------------------------------------------
  # This bit of messy code is compensate for the whitespace
  # between date and time on the start-date and end-date
  # argumements.
  #--------------------------------------------------------
  argDict = {}
  myArgs = ""
  for a in sys.argv[1:]:
    myArgs = myArgs + " " + a
  myargs = string.split(myArgs," --")
  for a in myargs:
    if len(a) == 0:
      continue
    ### print "   --%s" % a
    for k in args.keys():
      keyword = args[k]
      if string.find(a, keyword) == 0:
        argDict[k] = a[len(keyword):]

  #----------------------------------------------------------
  # Log file to parse - default is the Globus gatekeeper log 
  #----------------------------------------------------------
  gatekeeperLog = ""
  if argDict.has_key("logfile"):
    gatekeeperLog = argDict["logfile"]
  else:
    if os.environ.has_key("GLOBUS_LOCATION"):
      globusLocation = os.environ["GLOBUS_LOCATION"]
      gatekeeperLog = "%s/var/globus-gatekeeper.log" % globusLocation
    else:
      print "ERROR: $GLOBUS_LOCATION variable not set"
      print "       Set the GLOBUS_LOCATION variable or override using the --logfile option"
      raise SystemExit(1)
  if os.path.isfile(gatekeeperLog):
    pass
  else:
    print "ERROR: log file (%s) does not exist" % gatekeeperLog
    raise SystemExit(1)

  #----------------------------------------------------------
  # Start date - defaults to midnight of today 
  #----------------------------------------------------------
  DEFAULT_START_FORMAT = "%m/%d/%Y 00:00:00"
  startDate = strftime(DEFAULT_START_FORMAT,localtime(time())) 
  if argDict.has_key("start-date"): 
    startDate = argDict["start-date"]
  try:
    testtime = strptime(startDate,Gatekeeper.DEFAULT_DATE_FORMAT)
  except ValueError:
    print "ERROR: invalid date specified in --start-date=%s" % argDict["start-date"]
    print "       Date format is MM/DD/YYYY HH:MM:SS"
    raise SystemExit(1)
    
  #----------------------------------------------------------
  # End date - defaults to now 
  #----------------------------------------------------------
  
  endDate = strftime(Gatekeeper.DEFAULT_DATE_FORMAT,localtime(time()))
  if argDict.has_key("end-date"):
    endDate = argDict["end-date"]
  try:
    testtime = strptime(endDate,Gatekeeper.DEFAULT_DATE_FORMAT)
  except ValueError:
    print "ERROR: invalid date specified in --end-date=%s" % argDict["end-date"]
    print "       Date format is MM/DD/YY HH:MM:SS"
    raise SystemExit(1)
    
  #----------------------------------------------------------
  # debug level - defaults to -1 (no output) 
  #----------------------------------------------------------
  debug = [-1]
  if argDict.has_key("debug"):
    debug = string.split(argDict["debug"],":")
  try:
    for d in debug:
      i = int(d)
  except ValueError:
    print "ERROR: invalid debug level specified in --debug=%s" % argDict["debug"]
    print "       Format is screen level:file level (e.g.- 1:2 2:2 2)"
    raise SystemExit(1)
    
  #----------------------------------------------------------
  # debug file - defaults to progam_name.log 
  #----------------------------------------------------------
  debugFile = None
  if argDict.has_key("debug-file"):
    debugFile = argDict["debug-file"]

  #---------------------------------------------------------------------
  # mode - defaults "TOTALS" only 
  #        (not used yet, plan was to have multiple modes of running)
  #---------------------------------------------------------------------
  mode = "TOTALS"
  if argDict.has_key("mode"):
    mode = argDict["mode"]
  
  #-------------------------------------------------------------------------
  # testlimit - default "0" (zero) means it will process all 
  #             For testing, it just limits the number of log file lines to 
  #             read from the log file
  #-------------------------------------------------------------------------
  testlimit = 0
  if argDict.has_key("testlimit"):
    testlimit = argDict["testlimit"]
  
  #-------------------------------------------------------------------------
  # testlimit - default "0" (zero) means it will process all 
  #             For testing, it just limits the number of log file lines to 
  #             read from the log file
  #-------------------------------------------------------------------------
  vo = None
  if argDict.has_key("vo"):
    vo = argDict["vo"]
  
  return (vo,gatekeeperLog,startDate,endDate,debug,debugFile,mode,testlimit)
 
## MAIN #################################################################
def main(argv):
  myMethod = "main"

  #-- parse command line arguements ------------------------------
  selectedVO,gatekeeperLog,startDate,endDate,debug,debugFile,mode,testlimit = parseArgs(argv)

  #--------------------------------
  startPgm = time() 
  startTime = strftime('%m/%d/%Y %H:%M:%S',localtime(startPgm))

  #--------------------------------
  gk = Gatekeeper(debugFile,debug,testlimit,mode)
  gk.setInterval(startDate,endDate)  
  totals = gk.processLogFile(gatekeeperLog) 
  users = totals.keys()
  for user in users:
    counts = totals[user]
    print "%s %s %s %s %s" % (user,counts['submissions'],counts['failures'],counts['pings'],counts['failedjobs'])

  #--------------------------------
  endPgm = time() 
  endTime = strftime('%m/%d/%Y %H:%M:%S',localtime(endPgm))
  elapsed = endPgm - startPgm
  elapsedTime = strftime('%H:%M:%S',gmtime(elapsed))
  gk.log(myMethod,"Start time: %s End time: %s Elapsed time: %s" % (startTime,endTime,elapsedTime),0)
  gk.closeLog()
 
  return



#############################################################################
if __name__ == '__main__':
    # Do it!
    sys.exit(main(sys.argv))
#############################################################################
