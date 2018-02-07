/*
 * Created on Jun 20, 2006
 *
 */
package lia.util.fdt.xdr;


import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class XDRNamedPipe extends XDRGenericComm {

	protected File pipeIn;
	protected File pipeOut;

	public XDRNamedPipe(String pipeNameIn, String pipeNameOut) throws IOException {
		super("XDRNamedPipe for [ " + pipeNameIn + " - " + pipeNameOut + " ] ", new XDROutputStream(new FileOutputStream(new File(pipeNameOut))), new XDRInputStream(
				new FileInputStream(new File(pipeNameIn))));
	}

	//create a pipe (not open by default)
	public XDRNamedPipe(File pipeIn, File pipeOut) {
		super("XDRNamedPipe for [ " + pipeIn + " - " + pipeOut + " ] ");
		this.pipeIn = pipeIn;
		this.pipeOut=pipeOut;
	}
	
	protected void initSession() throws Exception {
		this.xdris = new XDRInputStream(new FileInputStream(this.pipeIn));
		this.xdros = new XDROutputStream(new FileOutputStream(pipeOut));
		super.closed = false;
	}

	protected void notifyXDRCommClosed() {

	}

	protected void xdrSession() throws Exception {
		// nothing to do on pipes

	}

}