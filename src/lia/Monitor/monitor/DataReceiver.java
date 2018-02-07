package lia.Monitor.monitor;


public interface DataReceiver {
 public void addResult ( Result r ) throws Exception  ;
 public void addResult ( eResult r ) throws Exception  ;
 public void addResult ( ExtResult r ) throws Exception  ;
 public void addResult ( AccountingResult r ) throws Exception  ;
 public void updateConfig ( MFarm farm ) throws Exception  ;
}


