package lia.Monitor.JiniClient.CommonGUI.Jogl;

/**
 * monitors the status of changing the projection
 *
 * Jan 6, 2005 - 2:05:09 PM
 */
public class ChangeProjectionStatus extends AbstractGlobeListener 
{
    public static final int PROJECTION_SET = 0;
    public static final int PROJECTION_CHANGING_TO_SPHERE = 1;
    public static final int PROJECTION_CHANGING_TO_MAP = 2;
    public static final int PROJECTION_CHANGING = 3;
    public static final int PROJECTION_SET_TO_SPHERE = 4;
    public static final int PROJECTION_SET_TO_MAP = 5;
    private int nValue = PROJECTION_SET;

    public void radiusChanged() {
    }

    public void radiusChangeStart() {
        setState(PROJECTION_CHANGING);
    }

    public void radiusChangeFinish() {
        setState(PROJECTION_SET);
    }
    
    private synchronized void setState(int nVal)
    {
        nValue = nVal;
    }
    
    /**
     * returns state of projection<br>
     * implemented for the moment only changing state, from one form to another (PROJECTION_CHANGING)<br>
     * and stable state, to one form (PROJECTION_SET).
     * @return an int value representing the state of projection (constants are available to explain the state).
     */
    public synchronized int getState()
    {
        return nValue;
    }

}