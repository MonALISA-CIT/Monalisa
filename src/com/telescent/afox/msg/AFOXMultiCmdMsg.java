package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXMultiCmdMsg implements Serializable, IHeader { 

	private static final long serialVersionUID = 4430788716002729700L;
	
	public int Header = getHeader();  
	public String MultiCmds;  //Simple string of commands separated by a carriage return. As in a simple text file with separate lines.
	public int NumBeginningLinesToSkip;  //This tell the Switch to ignore the first N lines.  For example if the file came directly from
	//a .cvs file that had a header that was not a command, this would be set to 1 in order to skip
	//the first line.
	public String UserName;
	public boolean ReturnNewerUpdates;
	public int MyCurSMUpdateID;
	public int MyCurPendingListUpdateID;

	private AFOXMultiCmdMsg() { }
	
	public AFOXMultiCmdMsg(
			String UserName_in,
			String MultiCmds_in,
			int NumBeginningLinesToSkip_in,
			boolean ReturnNewerUpdates_in,
			int MyCurSMUpdateID_in,
			int MyCurPendingListUpdateID_in 	) 	{ 
		MultiCmds = MultiCmds_in;
		NumBeginningLinesToSkip = NumBeginningLinesToSkip_in;
		UserName = UserName_in;
		ReturnNewerUpdates = ReturnNewerUpdates_in;
		MyCurSMUpdateID = MyCurSMUpdateID_in;
		MyCurPendingListUpdateID = MyCurPendingListUpdateID_in;
	}
	
	public ByteArrayOutputStream Serialize() { 
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		byte[] b = this.ToFlatSer();
		if (b == null) b = new byte[0];
		try {
			o.write(b);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return o;
	}

	public static AFOXMultiCmdMsg DeSerialize(ByteArrayOutputStream ms) { 
		AFOXMultiCmdMsg ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXMultiCmdMsg DeSerialize(byte[] b) { 
		AFOXMultiCmdMsg ci = FromFlatSer(b);
		return ci;
	}



	/***************************************************************************
	 *                                                                          *
	 *  Custom Memory Efficient Serializer/Deserialzer                          *
	 *  All variables very flat.  No headers etc.                               *
	 *                                                                          *
	 *  byte[] ToFlatSer() : returns byte array of serialized data              *
	 *                                                                          *  
	 *  FromFlatSer(UnflatSerialize uf) : returns an instance of this class     *
	 *    when supplied with the flattened serialized data stream.  uf has      *
	 *    its own internal pointer and will start in the data stream where      *
	 *    it left off in any previous call.  An error will be returned if       *
	 *    uf cannot deserialize an input.  If a deserialization failure         *
	 *    occurs, uf will no longer deseriaize data.  Therefore at the first    *
	 *    failure, the operation should be abondened.                           *
	 *                                                                          *
	 ***************************************************************************/
	//Serialzer   
	public byte[] ToFlatSer() { 
		FlatSerialize f = new FlatSerialize();
		Object o[] = new Object[7];
		o[0] = Header;
		o[1] = UserName;
		o[2] = MultiCmds;
		o[3] = NumBeginningLinesToSkip;
		o[4] = ReturnNewerUpdates;
		o[5] = MyCurSMUpdateID;
		o[6] = MyCurPendingListUpdateID;
		f.AddItems(o);
		return f.serializedData;
	}
	
	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXMultiCmdMsg FromFlatSer(byte[] ba) {
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 
	
	public static AFOXMultiCmdMsg FromFlatSer(UnflatSerialize uf) {
		AFOXMultiCmdMsg ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		String UserName_in;
		String MultiCmds_in;
		int NumBeginningLinesToSkip_in;
		boolean ReturnNewerUpdates_in;
		int MyCurSMUpdateID_in;
		int MyCurPendingListUpdateID_in;
		/* 2) get each item, (deserialize each item), in the proper order, (the same way 
		 * they were serialized).
		 * Check each "get" for success or failure.  If it fails, abondon the
		 * the rest of the deserialization and return an error.
		 */
		boolean success;
		while(true) { //while loop only done one time.  a "trick" for quick branching out if any step fails.
			int headerDummy;  //Need to read header, but we don't use it later to instantiate.
			
			success = uf.getInt();     
			if(!success) break;
			headerDummy = uf.intVal;
			
			success = uf.getString();     
			if(!success) break;
			UserName_in = uf.stringVal;

			success = uf.getString();     
			if(!success) break;
			MultiCmds_in = uf.stringVal;

			success = uf.getInt();     
			if(!success) break;
			NumBeginningLinesToSkip_in = uf.intVal;

			success = uf.getBoolean();     
			if(!success) break;
			ReturnNewerUpdates_in = uf.boolVal;

			success = uf.getInt();     
			if(!success) break;
			MyCurSMUpdateID_in = uf.intVal;

			success = uf.getInt();     
			if(!success) break;
			MyCurPendingListUpdateID_in = uf.intVal;

			//Check we have exactly 0 bytes left or we have an error
			success = uf.getNumBytesLeft() == 0; if(!success) break;

			if(success)  //must be success if we made it here
			{ ci = new AFOXMultiCmdMsg(
					UserName_in,
					MultiCmds_in,
					NumBeginningLinesToSkip_in,
					ReturnNewerUpdates_in,
					MyCurSMUpdateID_in,
					MyCurPendingListUpdateID_in
			);
			}

			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}

	public int getHeader() {
		return AFOXMsgs.TCP_AFOXMultiCmdMsg; 
	}

}
