package lia.Monitor.monitor;

import java.util.Vector;


public class MonModuleInfo implements java.io.Serializable{
    public String name;  // module name
    public String[] ResTypes;
    public int error_count=0;
    public long lastMeasurement;
    public int  type;
    public int state ;
    public int id ;
    static int ID =0;
    // vector used to record all the errors that have occured with
    // respect to this particulat monitoring module
    public Vector errorDesc = new Vector();


    public MonModuleInfo () {
        id = ID++;
    }
    public void addErrorCount(){
        error_count++;
    }
    public void setErrorCount(int c){
        error_count = c;
    }
    public int getErrorCount(){
        return error_count;
    }
    public void setName(String name){
        this.name=name;
    }
    public String getName(){
        return name;
    }
    public void setResType(String[] res){
        ResTypes=res;
    }
    public String[] getResType(){
        return ResTypes;
    }
    public void setLastMeasurement(long time){
        lastMeasurement=time;
    }
    public long getLastMeasurement(){
        return lastMeasurement;
    }
    public void setState(int state){
        this.state=state;
    }
    public int getState(){
        return state;
    }
    public void setErrorDesc(String desc){
        errorDesc.add(desc);
    }
    public Vector getErrorDesc(){
        return errorDesc;

    }

    public String toString() {
        return (name==null)?"":name;
    }
}



