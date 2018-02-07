package lia.Monitor.monitor;

public class MonCmd implements java.io.Serializable{
 public String cmd;
 public String type ;
 

public MonCmd ( String cmd, String type) {
   this.cmd = cmd ;
   this.type = type;
}

public MonCmd ( ) {  }
}

