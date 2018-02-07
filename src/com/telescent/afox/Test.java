package com.telescent.afox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.telescent.afox.msg.AFOXCmdRetMsg;
import com.telescent.afox.msg.AFOXFullUpdateReturnMsg;

public class Test {

	public static byte[] getBytesFromFile(File file) throws IOException { 
		InputStream is = new FileInputStream(file); 
		// Get the size of the file 
		long length = file.length(); 
		// Before converting to an int type, check 
		// to ensure that file is not larger than Integer.MAX_VALUE. 
		if (length > Integer.MAX_VALUE) { 
			throw new IOException("File is too large: "+file.getName()); 
		} 
		// Create the byte array to hold the data 
		byte[] bytes = new byte[(int)length]; 
		// Read in the bytes 
		int offset = 0; 
		int numRead = 0; 
		while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) { 
			offset += numRead; 
		} 
		// Ensure all the bytes have been read in 
		if (offset < bytes.length) { 
			throw new IOException("Could not completely read file "+file.getName()); 
		} 
		// Close the input stream and return bytes 
		is.close(); 
		return bytes; 
	}
	public static void main(String args[]) {
		// test AFOXCmdRetMsg....
		// first read all bytes...
		try {
			byte[] b = getBytesFromFile(new File("AFOXCmdRetMsg.bin"));
			AFOXCmdRetMsg m = AFOXCmdRetMsg.DeSerialize(b);
			// print smth for testig...
			System.out.println(m.LatestPendingUpdateID);
			System.out.println(m.Header);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// next test AFOXFullUpdateReturnMsg
		AFOXFullUpdateReturnMsg m = null;
		byte[] b = null;
		try {
			b = getBytesFromFile(new File("AFOXFullUpdateReturnMsg.bin"));
			m = AFOXFullUpdateReturnMsg.DeSerialize(b);
			// print smth for testig...
//			System.out.println(m.LatestPendingUpdateID);
			System.out.println(m);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// let's write back the bytes...
		if (m!=null) {
			try {
				byte[] b1 = m.ToFlatSer();
				if (b1.length != b.length) {
					System.err.println("err in length ("+"original-"+b.length+" new-"+b1.length+")");
					// output first 10 bytes for comparison
					for (int i=0; i<60; i++) {
						System.out.println(i+": "+b[i]+" - "+b1[i]);
					}
					int min = (b1.length < b.length) ? b1.length : b.length;
					for (int i=0; i<min; i++) {
						if (b[i] != b1[i]) {
							System.err.println("At "+i);
							break;
						}
					}
//					FlatSerialize f = new FlatSerialize();
//					f.AddItems(new Object[] { FlatSerialize.NoNull(m.SMCurrentIns[0][0].CustomerName) });
//					byte bv[] = f.serializedData;
//					for (int i=0; i<bv.length; i++) {
//						System.out.println(i+": "+bv[i]);
//					}
//
//					System.out.println("---------");
//					f = new FlatSerialize();
//					f.AddItems(new Object[] { FlatSerialize.NoNull(m.SMCurrentIns[0][0].AFOXName) });
//					bv = f.serializedData;
//					for (int i=0; i<bv.length; i++) {
//						System.out.println(i+": "+bv[i]);
//					}
//					System.out.println(m.SMCurrentIns[0][0].AFOXName.length() + " <-> "+bv.length);

					
				} else {
					for (int i=0; i<b.length; i++) {
						if (b[i] != b1[i]) {
							System.err.println("At "+i);
							break;
						}
					}
					
//					for (int i=53; i<70; i++) {
//						System.out.println(i+": "+b[i]+" - "+b1[i]);
//					}
//					FlatSerialize f = new FlatSerialize();
//					f.AddItems(new Object[] { FlatSerialize.NoNull(m.SMCurrentIns[0][0].CustomerName) });
//					byte bv[] = f.serializedData;
//					for (int i=0; i<bv.length; i++) {
//						System.out.println(i+": "+bv[i]);
//					}

					
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}
