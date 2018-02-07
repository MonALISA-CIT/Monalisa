import java.net.InetAddress;

import lia.util.net.NetMatcher;

public class UDPAccessConf {
    private NetMatcher[] ipList;
    private boolean[] ipListPolicy;
    private String password;
    
    public UDPAccessConf() {
        ipList = null;
        ipListPolicy = null;
        password = null;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean checkPassword(String passToCheck) {
        if (password == null || password.length() == 0) return true;
        
        if (passToCheck == null || passToCheck.length() == 0) return false;
        
        return password.equals(passToCheck);
    }
    
    public boolean isPasswordDefined(){
        return (password != null) && (password.length() > 0);
    }
    
    public void addIP(String ip, boolean policy) {
        if (ip != null && ip.length() > 0) {
            NetMatcher nm = new NetMatcher(new String[]{ip});
            if(ipList == null) {
                ipList = new NetMatcher[1];
                ipListPolicy = new boolean[1];
            } else {
                NetMatcher[] ipListTmp = new NetMatcher[ipList.length + 1];
                boolean[] ipListPolicyTmp = new boolean[ipListPolicy.length + 1];
                System.arraycopy(ipList,0,ipListTmp,0,ipList.length);
                System.arraycopy(ipListPolicy,0,ipListPolicyTmp,0,ipListPolicy.length);
                ipList = ipListTmp;
                ipListPolicy = ipListPolicyTmp;
            }
            
            ipList[ipList.length - 1] = nm;
            ipListPolicy[ipListPolicy.length - 1] = policy;
        }
    }
    
    public boolean checkIP(InetAddress ip) {
        if (ipList == null || ipList.length == 0){
            return true;
        }
        
        for (int i = 0; i < ipList.length; i++) {
            if (ipList[i].matchInetNetwork(ip)) {
                return ipListPolicy[i];
            }
        }
        
        return false;
    }
    
    public String toString(){
        StringBuffer sb = new StringBuffer(2048);
        sb.append("\n\nPassword: " + password);
        if (ipList != null) {
            for (int i = 0; i < ipList.length; i++) {
                sb.append("\n["+i+"] = "+ipList[i].toString()+" ["+ipListPolicy[i]+"]" );
            }
            
        }
        sb.append("\n\n");
        return sb.toString();
    }
}
