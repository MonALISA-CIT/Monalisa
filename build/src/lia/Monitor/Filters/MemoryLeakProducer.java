package lia.Monitor.Filters;

import java.util.Vector;

import lia.Monitor.monitor.monPredicate;


public class MemoryLeakProducer extends GenericMLFilter {

    private Vector hardRef ;
    private byte[] step ;

    private static String Name = "MLMemWatcher";

    public MemoryLeakProducer(String farmName) {
        super(farmName);
        hardRef = new Vector();
    }

    /* (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#getName()
     */
    public String getName() {
        // TODO Auto-generated method stub
        return Name;
    }
    /* (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    public long getSleepTime() {
        // TODO Auto-generated method stub
        return 30*1000;
    }

    /* (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#getFilterPred()
     */
    public monPredicate[] getFilterPred() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#notifyResult(java.lang.Object)
     */
    public void notifyResult(Object o) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#expressResults()
     */
    public Object expressResults() {
        // TODO Auto-generated method stub
        step = new byte[1024*512];//512k
        hardRef.add(step);
        return null;
    }

}
