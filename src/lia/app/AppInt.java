package lia.app;

public interface AppInt {

    public boolean start();
    public boolean stop();
    public boolean restart();
    public int     status();
    public String  info();
    public String  exec(String sCmd);
    public boolean update(String sUpdate);
    public boolean update(String sUpdate[]);
    
    public String  getConfiguration();
    public boolean updateConfiguration(String s);

    public boolean init(String sPropFile);
    
    public String  getName();
    public String  getConfigFile();

}
