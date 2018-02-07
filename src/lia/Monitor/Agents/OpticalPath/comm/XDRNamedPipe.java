package lia.Monitor.Agents.OpticalPath.comm;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class XDRNamedPipe extends XDRGenericComm {

    
    public XDRNamedPipe(String pipeNameIn, String pipeNameOut, XDRMessageNotifier notifier) throws IOException{
        super("XDRNamedPipe for [ " + pipeNameIn + " - " + pipeNameOut + " ] ",
                new XDROutputStream(new FileOutputStream(new File(pipeNameOut))),
                new XDRInputStream(new FileInputStream(new File(pipeNameIn))),
                notifier);
    }
    
    public XDRNamedPipe(File pipeIn, File pipeOut, XDRMessageNotifier notifier) throws IOException{
        super("XDRNamedPipe for [ " + pipeIn + " - " + pipeOut + " ] ",
                new XDROutputStream(new FileOutputStream(pipeOut)),
                new XDRInputStream(new FileInputStream(pipeIn)),
                notifier);
    }
    
}
