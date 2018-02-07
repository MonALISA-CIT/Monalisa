package lia.monitoring.phedex;
import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Vector;

import lia.monitoring.sqlpool.ConnectionPool;
import lia.util.DateFileWatchdog;

public class PhedexFetcher extends Thread implements Observer {
    
    private DateFileWatchdog dfw = null;
    private File confFile;

    private String jdbcURL;
    private String userName;
    private String passwd;


    private Object syncConf = new Object();
    private Object exchanger = new Object();

    private long sleepTime;

    private boolean hasToRun;

    private Vector localStore;

    private boolean newOraConf;
    MLPhedexTable[] mlpt;
    
    long lastTime;
    ConnectionPool connPool;
    
    private PhedexFetcher() throws Exception {//only for testing
        this("/home/ramiro/ML_Phedex/Service/usr_code/PhEDEx/conf/ML_PhEDEx.properties");
    }
    
    public PhedexFetcher(String confFileName) throws Exception {
        this(new File(confFileName));
    }
    
    public PhedexFetcher(File confFile) throws Exception {
        setName(" ( ML ) - MonALISA PhEDExFetcher Thread");
        hasToRun = false;
        localStore = new Vector();
        this.confFile = confFile;
        dfw = DateFileWatchdog.getInstance(confFile, 10 * 1000);
        dfw.addObserver(this);
        loadConf();
        hasToRun = true;
    }
    
    private void setConf(String jdbcURL, String userName, String passwd, MLPhedexTable[] mlpt, long sleepTime) {
        synchronized (syncConf) {
            if (jdbcURL != null) {
                if (this.jdbcURL == null || !this.jdbcURL.equalsIgnoreCase(jdbcURL)) newOraConf = true;
                this.jdbcURL = jdbcURL;
            }

            if (userName != null) {
                if (this.userName == null || !this.userName.equals(userName)) newOraConf = true;
                this.userName = userName;
            }

            if (passwd != null) {
                if (this.passwd == null || !this.passwd.equals(passwd)) newOraConf = true;
                this.passwd = passwd;
            }
            
            this.mlpt = mlpt;
            
            this.sleepTime = sleepTime;
        }
    }

    private Connection getConnection() {
        Connection conn = null;
        try {
            if (newOraConf) {
                System.out.println("\n\n [ " + new Date() + " ] New Ora Conf ... ");
                if (connPool != null) {
                    connPool.closeAllConnections();
                }
                connPool = new ConnectionPool("oracle.jdbc.driver.OracleDriver", jdbcURL, userName, passwd, 1, 1, false);
            }
            conn = connPool.getConnection();
            newOraConf = false;
        } catch (Throwable t) {
            newOraConf = true;
            System.out.println("Got exception trying to (re)use a connection");
            t.printStackTrace();
            //almost no chance ...
            releaseConnection(conn);
            conn = null;
        }
        return conn;
    }

    private void releaseConnection(Connection conn) {
        if (conn == null) return;
        try {
            connPool.free(conn);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    public Vector getData() {
        Vector retV = null;
        synchronized(exchanger) {
            if(localStore != null && localStore.size() > 0) {
                retV = new Vector(localStore);
                localStore.clear();
            }
        }
        return retV;
    }
    
    public void run() {
        
        while(hasToRun) {
            //sleep
            try{
                Thread.sleep(sleepTime);
            }catch(Throwable t){};

            try {
                Connection conn = null;
                Vector totalV = new Vector();
                try {
                    conn = getConnection();
                    synchronized(syncConf) {
                        if(mlpt != null) {
                            for(int i=0;i<mlpt.length; i++) {
                                MLPhedexTable mlptable = mlpt[i];
                                Vector tv = mlptable.getData(conn);
                                totalV.addAll(tv);
                            }
                        }
                    }
                }catch(Throwable t) {
                    t.printStackTrace();
                }finally{
                    if(conn != null) {
                        releaseConnection(conn);
                        conn = null;
                    }
                }
                
                synchronized(exchanger) {
                    if(totalV.size()>0) {
                        localStore.addAll(totalV);
                    }
                }
            }catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    private void loadConf() {
        String newJdbcURL = null;
        String newUserName = null;
        String newPasswd = null;
        Vector MLPTV = new Vector();
        
        try {
            Properties p = new Properties();
	    FileInputStream fis = new FileInputStream(confFile);
            p.load(fis);
	    fis.close();
	    
            newJdbcURL = p.getProperty("jdbcUrl");
            newUserName = p.getProperty("username");
            newPasswd = p.getProperty("passwd");
            
            if(newJdbcURL != null) newJdbcURL = newJdbcURL.trim();
            if(newUserName != null) newUserName = newUserName.trim();
            if(newPasswd != null) newPasswd = newPasswd.trim();

            int i=1;
            String cTableName = p.getProperty("tablename_"+i);
            
            while(cTableName!=null) {
                try {
                    cTableName = cTableName.trim();
                    String cClusterName = p.getProperty("ML_ClusterName_"+i);
                    if(cClusterName != null) {
                        cClusterName = cClusterName.trim();
                        if(cClusterName.length() >0) {
                            String cKeys = p.getProperty("keys_"+i);
                            if(cKeys != null) {
                                String cKeysA[] = cKeys.trim().split("(\\s)*,(\\s)*");
                                if(cKeysA != null && cKeysA.length >0) {
                                    String cParms = p.getProperty("params_"+i);
                                    if(cParms != null) {
                                        String cParamsA[] = cParms.trim().split("(\\s)*,(\\s)*");
                                        if(cParamsA != null && cParamsA.length > 0) {
                                            String keySep = p.getProperty("keys_separator_" + i);
                                            if(keySep != null){
                                                keySep = keySep.trim();
                                            }
                                            
                                            boolean bRateValues = false;
                                            String sRateValues = p.getProperty("Calculate_Rate_Values_"+i);
                                            if(sRateValues != null) {
                                                sRateValues = sRateValues.trim();
                                                try {
                                                    bRateValues = Boolean.valueOf(sRateValues).booleanValue();
                                                }catch(Throwable t){
                                                    bRateValues = false;
                                                }
                                            }
                                            
                                            String sMFactors =  p.getProperty("factors_"+i);
                                            String mFact[] = null;
                                            if(sMFactors != null && sMFactors.length() !=0 ){
                                                mFact = sMFactors.split("(\\s)*,(\\s)*");
                                            }
                                            
                                            //eventual directly typed SQL clauses
                                            String sqlConstraint =  p.getProperty("sqlConstraints_"+i);
                                            
                                            //OK - Let's try it
                                            MLPhedexTable mlpt = new MLPhedexTable(cTableName, cClusterName, cParamsA, mFact, cKeysA, keySep, sqlConstraint, bRateValues);
                                            MLPTV.add(mlpt);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }catch(Throwable t){
                    t.printStackTrace();
                }
                i++;
                cTableName = p.getProperty("tablename_"+i);
            }//while
        }catch(Throwable t) {
            
        }
        synchronized(syncConf) {
            setConf(newJdbcURL, newUserName, newPasswd, (MLPhedexTable[])MLPTV.toArray(new MLPhedexTable[MLPTV.size()]), 40*1000);
        }
    }
    
    public static final void main(String[] arg) throws Exception {
        PhedexFetcher pf = new PhedexFetcher(); 
        pf.start();
        
        for(;;){
            try {
                Thread.sleep(10 * 1000);
            }catch(Throwable t){
            }
            Vector v = pf.getData();
            if(v != null && v.size() != 0) {
                System.out.println("\n\n GOT [ " + v.size() + " ] ELEMENTS");
                for(int i=0; i<v.size(); i++) {
                    System.out.println(" [ " + i + " ]  === " + v.elementAt(i));
                }
            }else{
                System.out.println("\n\n No DATA!!! \n\n");
            }
        }
    }

    public void update(Observable o, Object arg) {
        if(o != null && dfw != null && o.equals(dfw)) {
            loadConf();
        }
    }
}
