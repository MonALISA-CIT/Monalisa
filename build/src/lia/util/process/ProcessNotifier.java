/*
 * $Id: ProcessNotifier.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 * Created on Oct 8, 2010
 */
package lia.util.process;

import java.util.List;

/**
 * 
 * @author ramiro
 */
public interface ProcessNotifier {
    public void notifyStdOut(ExternalProcess p, List<String> line);

    public void notifyStdErr(ExternalProcess p, List<String> line);

    public void notifyProcessFinished(ExternalProcess p);
}
