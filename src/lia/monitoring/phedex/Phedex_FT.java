package lia.monitoring.phedex;
//package cms;

import java.io.File;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

public class Phedex_FT extends cmdExec implements MonitoringModule {


    Vector rez;
    Hashtable lastValues;
    
    static public String ModuleName = "PhEDEx_FT";
    
    File confFile = null;
    PhedexFetcher pf = null;
    public Phedex_FT() {
        super("PhEDEx_FT");
        lastValues = new Hashtable();
        System.out.println("Start the Interface to PhEDEx_FT ");
        isRepetitive = true;
        rez = new Vector();
    }

    public String[] ResTypes() {
        return new String[]{};
    }

    public MonModuleInfo init(MNode Node, String arg1) {
        System.out.println(" INIT PhEDEx_FT  MODULE " + arg1);
        this.Node = Node;
        
        try {
            if (arg1 != null) {
                String tokens[] = arg1.split("(\\s)*,(\\s)*");
                
                if (tokens != null && tokens.length > 0 ) {
                    for(int i = 0; i < tokens.length; i++) {
                        if(tokens[i] != null) tokens[i] = tokens[i].trim();
                        if(tokens[i].indexOf("ConfFile") != -1 && tokens[i].indexOf("=") != -1) {
                            String[] ft = tokens[i].split("(\\s)*=(\\s)*");
                            if(ft != null && ft.length == 2) {
                                confFile = new File(ft[1]);
                            }
                        }
                    }
                }
            }
        }catch(Throwable t) {
            t.printStackTrace();
        }
        
        if(confFile == null)  {
            System.out.println("\n\nPhEDEx_FT NO CONF FILE! Will NOT START! ");
        } else {
            try {
                this.pf = new PhedexFetcher(confFile);
                this.pf.start();
            }catch(Throwable t){
                System.out.println("\n\nPhEDEx_FT while intializing PhedexFetcher ");
                t.printStackTrace();
            }
        }
        return info;
    }


    public String getOsName() {
        return "linux";
    }

    public Object doProcess() throws Exception {
        return pf.getData();
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    
    static public void main(String[] args) {

        String host = "localhost";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        Phedex_FT aa = new Phedex_FT();
        
        if(args.length == 0) {
            aa.init(new MNode(host, ad, null, null), "ConfFile=/home/ramiro/ML_Phedex/Service/usr_code/PhEDEx/conf/ML_PhEDEx.properties");
        } else {
            aa.init(new MNode(host, ad, null, null), "ConfFile="  +  args[0]);
        }

        for (;;) {
            try {
                Object bb = aa.doProcess();
                System.out.println(" End Do process " + bb);
                if (bb != null && bb instanceof Vector) {
                    System.out.println(" MAIN ===================");

                    System.out.println(" Received a Vector having " + ((Vector) bb).size() + " results");
                    Vector cc = (Vector) bb;
                    for (int i = 0; i < cc.size(); i++) {
                        Result r = (Result) cc.elementAt(i);
                        System.out.println("===>" + r);
                    }
                }
            } catch (Exception e) {
                System.out.println(" failed to process ");
                e.printStackTrace();
            }
            try {
                Thread.sleep(30 * 1000);//obly for testing
            } catch (Exception eion) {
                ;
            }
        }

    }

}

