package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.util.logging.Level;
import java.util.logging.Logger;

/** this class is used to compute the new layout in a separate thread than AWT.
 * This way it is possible to perform more redraws of the interface for the same layout,
 * since the command to recreate the layout comes from the interface, and therefore AWT. 
 */
public class LayoutTransformer extends Thread {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(LayoutTransformer.class.getName());

    private final Object sync = new Object();
    LayoutChangedListener listener;

    public LayoutTransformer(LayoutChangedListener listener) {
        super();
        this.listener = listener;
        start();
    }

    public void layoutChanged() {
        synchronized (sync) {
            sync.notify();
        }
    }

    @Override
    public void run() {
        setName("(ML) LayoutTransformer");
        while (true) {
            try {
                synchronized (sync) {
                    sync.wait();
                }
                listener.computeNewLayout();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error while computingNewLayout", t);
            }
        }
    }
}