import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.DateFileWatchdog;
import lia.util.MLProcess;
import lia.util.ntp.NTPDate;
import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicLong;
import edu.emory.mathcs.backport.java.util.Arrays;

/**
 * 
 */
public class PipeResultWriter extends GenericMLFilter implements AppConfigChangeListener, Observer {


    /**
     * Just because GenericMLFilter implements java.io.Serializable 
     */
    private static final long serialVersionUID = -7092400235761953487L;
    
    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger(PipeResultWriter.class.getName());
    private AtomicLong sleepTime;
    private static String Name = "PipeResultWriter";
    Properties localConfProp = null;
    
    private Object buffLock;
    private Object lPropLock;
    private Object bwLock;
    
    private long lastPrintedStatus;
    private long PRINT_STATUS_DELAY;
    
    private static final Object[] EMPTY_BUFFER = new Object[0];
    
    //circular buffer used to keep the Result-s in mem
    private Object buffer[];
    int start, count;
    
    private monPredicate[] predicates;
    
    //Pipe Name
    private String pipeName;
    private BufferedWriter bw;
    
    File localPropsFile;
    DateFileWatchdog dfw;
    
    public PipeResultWriter(String farmName) {
        super(farmName);
        
        buffLock = new Object();
        lPropLock = new Object();
        bwLock = new Object();
        
        predicates = null;
        localPropsFile = null;
        dfw = null;
        
        buffer = new Object[1000];
        count = 0;
        start = 0;
        PRINT_STATUS_DELAY = 20 * 1000;
        sleepTime = new AtomicLong(5 * 1000);
        AppConfig.addNotifier(this);
        localConfProp = new Properties();
        reloadConfig();
    }

    /* from MonitorFilter */
    public String getName() {
        return Name;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    public long getSleepTime() {
        return sleepTime.get();
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getFilterPred()
     */
    public monPredicate[] getFilterPred() {     return predicates;   }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#notifyResult(java.lang.Object)
     */
    public void notifyResult(Object o) {
        if (o == null) return;
        synchronized(buffLock) {
            int idx = (start+count)%buffer.length;
            buffer[idx] = o;
            if (count < buffer.length) {
                count++;
            } else {
                start++;
            }
        }
        if(logger.isLoggable(Level.FINER)) {
            long now = NTPDate.currentTimeMillis();
            if(lastPrintedStatus + PRINT_STATUS_DELAY < now ) {
                logger.log(Level.FINER, getCurrentStatus());
                lastPrintedStatus = now;
            }
        }
    }
    
    private void mkFifo(String pipeName) {
        if(pipeName == null) return;
        try {
            Process pro = null;
            pro = MLProcess.exec(new String[] { "mkfifo",  pipeName });
            pro.waitFor();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ " + Name + " ] Cannot create named PIPE [ " + pipeName +" ]", t);
        }
    }

    public String getCurrentStatus() {
        StringBuffer sb = new StringBuffer(1024);
        synchronized(buffLock) {
            sb.append("\n\n ===== ").append(Name).append(" Status dT [ ").append(PRINT_STATUS_DELAY).append(" ] ==== \n");
            sb.append("\n PipeName : ").append(pipeName).append("\n");
            sb.append("\n Publish dT [ ").append(sleepTime.get()).append(" ] ms\n");
            sb.append("\n Buff Size: ").append(buffer.length).append(" Current Buffer Count: ").append(count);
            sb.append("\n Buff Start idx: ").append(start).append(" [ overlapped == ").append(!(start == 0)).append(" ]");
            sb.append("\n\n ===== End ").append(Name).append(" Status ==== \n\n");
        }
        return sb.toString();
    }
    
    private Object[] getAndClearBuffer() {
        synchronized(buffLock) {
            if(count == 0) return EMPTY_BUFFER;
            Object[] rbuff = new Object[count];
            for (int i = 0; i < count; i++) {
                int idx = (start+i)%buffer.length;
                rbuff[i] = buffer[idx];
                buffer[idx] = null;//GC the records
            }
            // Empty the buffer.
            start = 0;
            count = 0;
            return rbuff;
        }
    }
    
    private void initWriter() {
        try {
            synchronized(bwLock) {
                cleanup();
                if(pipeName != null) {
                    bw = new BufferedWriter(new FileWriter(pipeName));
                }
            }
        }catch(Throwable t) {
            logger.log(Level.WARNING, " [ " + Name + " ] Cannot init writer", t);
            cleanup();
        }
    }
    
    private void publish(Object o) {
        if(logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ " + Name + " ] Start publishing ... [ " + o + " ]" );
        }

        try {
            String formatedResult = textFormat(o);
            if(formatedResult != null) {
                synchronized(bwLock) {
                    if(bw == null) return;
                    bw.write(formatedResult);
                    bw.newLine();
                    bw.flush();
                }
            }
        } catch(IOException ioe) {
            logger.log(Level.WARNING, " [ " + Name + " ] Got IOException trying to write ... ", ioe);
            try {
                cleanup();
                //maybe someone removed the pipe ( by mistake )
                mkFifo(pipeName);
            }catch(Throwable t) {//ignore exc on close()
            } finally {
                bw = null;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ " + Name + " ] [ publish ] Got Gen exc", t);
        }
    }

    private String textFormat(Object o) {
        try {
            if(o instanceof Result) {
                return formatResult((Result)o);
            } else if( o instanceof eResult) {
                return formateResult((eResult)o);
            }
        }catch(Throwable t) {
            logger.log(Level.WARNING, " [ " + Name + " ] Got exception formating output", t);
        }
        return null;
    }
    
    private String formatResult(Result r) {
        StringBuffer sb = new StringBuffer();
        sb.append(" --> \t").append(r.NodeName);
        sb.append("\t ").append(r.ClusterName);
        sb.append("\t").append(r.FarmName);
        sb.append("\tTime = ").append(r.time) ;
        if(r.param_name == null) {
            sb.append("    param_name is null");
            if(r.param == null) {
                sb.append("    and param is also null");
            } else {
                sb.append("    and param [ length = ").append(r.param.length).append(" ] is NOT");
                for ( int i=0; i < r.param.length ; i++ ) {
                    sb.append("     * param[ ").append(i).append(" ] = ").append(r.param[i]).append("\t");
                 }
            }
            return sb.toString();
        }
        for ( int i=0; i < r.param_name.length ; i++ ) {
           sb.append("     * ").append(r.param_name[i]).append(" = \t").append(r.param[i]);
        }
        return sb.toString();
    }
    
    private String formateResult(eResult er) {
        StringBuffer sb = new StringBuffer();
        sb.append(" --> \t").append(er.NodeName);
        sb.append("\t ").append(er.ClusterName);
        sb.append("\t").append(er.FarmName);
        sb.append("\tTime = ").append(er.time) ;
        if(er.param_name == null) {
            sb.append("    param_name is null");
            if(er.param == null) {
                sb.append("    and param is also null");
            } else {
                sb.append("    and param [ length = ").append(er.param.length).append(" ] is NOT");
                for ( int i=0; i < er.param.length ; i++ ) {
                    sb.append("     * param[ ").append(i).append(" ] = ").append(er.param[i]).append("\t");
                 }
            }
            return sb.toString();
        }
        for ( int i=0; i < er.param_name.length ; i++ ) {
           sb.append("     * ").append(er.param_name[i]).append(" = \t").append(er.param[i]);
        }
        return sb.toString();
    }
    
    /**
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    public Object expressResults() {   
        try {
            synchronized(bwLock) {
                if(bw == null) {
                    initWriter();
                }
                if(bw == null) return null;
            }

            Object[] buffToPublish = getAndClearBuffer();
            for(int i=0; i < buffToPublish.length; i++) {
                try {
                    publish(buffToPublish[i]);
                } catch(Throwable t) {
                    logger.log(Level.WARNING, " [ " + Name + " ] Got exc expressResults() publishing record [ " + buffToPublish[i] + " ]", t);
                }
            }
        } catch ( Throwable t1){
            logger.log(Level.WARNING, " [ " + Name + " ] Got exc expressResults() main loop", t1);
        }
        return null;  
    }

    private void reloadLocalConfig() {
        Properties tmpProps = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(localPropsFile);
            tmpProps.load(new BufferedInputStream(fis));
        }catch(Throwable t) {
            logger.log(Level.WARNING, " Got exception loading local conf", t);
            return;
        } finally {
            try {
                fis.close();
            }catch(Throwable ignore){}
            fis = null;
        }

        synchronized(lPropLock) {
            if(tmpProps != null) {
                localConfProp.clear();
                localConfProp.putAll(tmpProps);
            } else {
                logger.log(Level.WARNING, " [ " + Name + " ] LocalProps cannot be loaded ... will keep old/default config");
                return;
            }
            
            long sleepT = 5 * 1000;
            try {
                sleepT = Long.parseLong(localConfProp.getProperty("sleepTime", "5000"));
            }catch(Throwable t){
                if(logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ " + Name + " ] Got exc parsing prop sleepTime", t);
                }
                sleepT = 5 * 1000;
            }
            
            if ( sleepT < 100) {
                sleepT = 100;
            }
            
            sleepTime.set(sleepT);
            
            sleepT = 1000L;
            try {
                sleepT = Long.parseLong(localConfProp.getProperty("PRINT_STATUS_DELAY", "20000"));
            }catch(Throwable t){
                if(logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ " + Name + " ] Got exc parsing prop PRINT_STATUS_DELAY", t);
                }
                sleepT = 1000;
            }
            
            if ( sleepT < 1000) {
                sleepT = 1000;
            }
            
            PRINT_STATUS_DELAY = sleepT;
            
            int newSize = 1000;
            try {
                newSize = Integer.parseInt(localConfProp.getProperty("BUFFER_SIZE", "1000"));
            }catch(Throwable t){
                if(logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ " + Name + " ] Got exc parsing prop BUFFER_SIZE", t);
                }
                newSize = 1000;
            }
            
            if(newSize < 0) {
                newSize = 0;
            }
            
            if(newSize != buffer.length) {
                resizeBuffer(newSize);
            }
            
            String newPipeName = null;
            try {
                newPipeName = localConfProp.getProperty("PIPE_NAME", null);
            } catch(Throwable t) {
                if(logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ " + Name + " ] Got exc parsing prop PIPE_NAME", t);
                }
                newPipeName = null;
            }
            
            if(newPipeName != null) {
                File testFile = new File(newPipeName);
                if(testFile.exists()) {
                    if(!testFile.canWrite()) {
                        logger.log(Level.WARNING, " [ " + Name + " ] Pipe [ " + newPipeName + " ] does not have write permissions");
                    } else {
                        if(pipeName == null || !pipeName.equals(newPipeName)) {
                            pipeName = newPipeName; 
                            mkFifo(pipeName);
                            cleanup();
                        }
                    }
                } else {
                    if(pipeName == null || !pipeName.equals(newPipeName)) {
                        pipeName = newPipeName; 
                        mkFifo(pipeName);
                        cleanup();
                    }
                }
            }
            
            String PredS = null;
            try {
                PredS = localConfProp.getProperty("PREDICATES", null);
                
            }catch(Throwable t) {
                logger.log(Level.WARNING, " [ " + Name + " ] Got exception reading PREDICATES property", t);
                PredS = null;
            }
            
            if(PredS != null) {
                PredS = PredS.trim();
                if(PredS.length() > 0) {
                    predicates = parsePredicates(PredS);
                } else {
                    predicates = null;
                }
            } else {
                predicates = null;
            }
            
            if(predicates == null) {
                logger.log(Level.INFO, " [ " + Name + " ] NO Predicates will filter the flow");
            } else {
                logger.log(Level.INFO, " [ " + Name + " ] will use the following monPredicates[] to filter the flow:\n " + Arrays.toString(predicates) + "\n");
            }
        }
    }

    private void cleanup() {
        synchronized(bwLock) {
            try {
                if (bw != null) {
                    bw.close();
                }
            }catch(Throwable ignore){
                
            }finally{
                bw = null;
            }
        }

    }
    private monPredicate[] parsePredicates(String predicatesS) {
        String[] pPreds = predicatesS.split("(\\s)*;(\\s)*");
        if(logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n [ " + Name + " ] Trying to parse predicates: " + Arrays.toString(pPreds) + " \n\n");
        }
        ArrayList alPreds = new ArrayList();
        for(int i=0; i<pPreds.length; i++) {
            try {
                alPreds.add(toPred(pPreds[i]));
            }catch(Throwable t) {
                logger.log(Level.WARNING, " [ " + Name + " ] Got exception parsing predicate [ " + pPreds[i] + " ]", t);
            }
        }
        if(alPreds.size() == 0) {
            return null;
        }
        return (monPredicate[])alPreds.toArray(new monPredicate[alPreds.size()]);
    }
    
    public static final monPredicate toPred(final String s) {
        if (s == null || s.trim().length() <= 0)
            return null;

        final StringTokenizer st = new StringTokenizer(s, "/");

        if (!st.hasMoreTokens())
            return null;

        final String sFarm = st.hasMoreTokens() ? st.nextToken() : "*";
        final String sCluster = st.hasMoreTokens() ? st.nextToken() : "*";
        final String sNode = st.hasMoreTokens() ? st.nextToken() : "*";

        final String sTime1 = st.hasMoreTokens() ? st.nextToken() : "-1";
        final String sTime2 = st.hasMoreTokens() ? st.nextToken() : "-1";
        
        // The default format is F/C/N/time1/time2/P
        // but it accepts two alternatives:
        // F/C/N/time1/P    with time2 = -1
        // F/C/N/P          with time1 = time2 = -1
        // the alternatives are chosen based on the existence of the other parameters and on the fact that they are numeric or not
        String sFunc = st.hasMoreTokens() ? st.nextToken() : null;

        long lTime1, lTime2;

        try {
            lTime1 = Long.parseLong(sTime1);

            try {
                lTime2 = Long.parseLong(sTime2);
            } catch (Exception e) {
                lTime2 = -1;
                sFunc = sTime2;
            }
        } catch (Exception e) {
            lTime1 = lTime2 = -1;
            sFunc = sTime1;
        }

        String[] vsFunc;
        if (sFunc == null) {
            vsFunc = null;
        } else {
            final Vector v = new Vector();
            final StringTokenizer st2 = new StringTokenizer(sFunc, "|");

            while (st2.hasMoreTokens()) {
                v.add(st2.nextToken().trim());
            }

            if (v.size() > 0) {
                vsFunc = new String[v.size()];
                for (int j = 0; j < v.size(); j++)
                    vsFunc[j] = (String) v.get(j);
            } else {
                vsFunc = null;
            }
        }

        return new monPredicate(sFarm, sCluster, sNode, lTime1, lTime2, vsFunc, null);
    }
    
    private void resizeBuffer(int newSize) {
        int minCount = 0;
        int oldSize = 0;
        
        synchronized(buffLock) {
            oldSize = buffer.length;
            Object[] newBuff = new Object[newSize];
            Object[] oldBuff = getAndClearBuffer();
            
            minCount = (newSize < oldBuff.length)?newSize:oldBuff.length;
            
            if(minCount > 0) {
                System.arraycopy(oldBuff, 0, newBuff, 0, minCount);
            }// else EMPTY_BUFFER
            
            buffer = newBuff;
            
            count = minCount;
            start = minCount;
        }//end synch
        
        logger.log(Level.INFO, " [ " + Name + " ] resizeBuffer() oldSize [ " + oldSize + " ] newSize [ " + newSize + " ]  copied [ " + minCount + " ] records ");
    }
    
    private void reloadConfig() {
        
        
        try {
            String localPropsFileName = AppConfig.getProperty("PipeResultWriter.ConfigFile", null);
            if(localPropsFileName == null) {
                logger.log(Level.WARNING, " [ " + Name + " ] \n\n Please specify PipeResultWriter.ConfigFile in ml.properties \n\n");
                return;
            }
            
            File localPropsFileTMP = new File(localPropsFileName) ;
            
            if(!localPropsFileTMP.exists() || !localPropsFileTMP.canRead()) {
                logger.log(Level.WARNING, "\n\n [ " + Name + " ] Please check that PipeResultWriter.ConfigFile [ " +  localPropsFileName + " ] exists and can be read! \n\n");
                return;
            }
            
            
            if ( localPropsFile == null || !localPropsFile.equals(localPropsFileTMP)) {
                localPropsFile = localPropsFileTMP;
                if(dfw != null) {
                    try {
                        dfw.stopIt();
                    }catch(Throwable t) {
                        logger.log(Level.WARNING, " [ " + Name + " ] Got exception trying to stop older DateFileWatchdog", t);
                    }
                    dfw = null;
                }
                try {
                    dfw = new DateFileWatchdog(localPropsFile, 5 * 1000);
                    dfw.addObserver(this);
                }catch(Throwable t) {
                    logger.log(Level.WARNING, " [ " + Name + " ] Got exception trying to start DateFileWatchdog", t);
                }
                reloadLocalConfig();
            }
        }catch(Throwable t) {
            logger.log(Level.WARNING, " [ " + Name + " ] Got exception trying to read local conf", t);
        }
        
    }
    
    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    public void update(Observable o, Object arg) {
        if( o != null && dfw != null && o.equals(dfw)) {
            logger.log(Level.INFO, " [ " + Name + " ] Config file changed ...  reloadLocalConfig");
            reloadLocalConfig();
        } else {
            logger.log(Level.WARNING, " [ " + Name + " ] Got a notification ... but no such dfw");
        }
    }

}
