package lia.Monitor.JiniClient.Farms.Histograms;

import lia.Monitor.JiniClient.CommonGUI.rcNode;


public class WLink implements java.io.Serializable  {

 public rcNode baseNode;
 public String fromIP;
 public String toIP;
 public String name ;
 public double in;
 public double out;
 public double speed; 
 public int index;
 public long time;

public WLink ( String name, rcNode bNode ) {
 this.name = name;
 this.baseNode = bNode;
}
  

}
