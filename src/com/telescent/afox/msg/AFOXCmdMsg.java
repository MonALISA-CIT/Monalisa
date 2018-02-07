package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXCmdMsg implements Serializable, IHeader { 

	private static final long serialVersionUID = 7966014104963668952L;

	public int Header = getHeader(); 

	public String Cmd;                //AFOX command such as a reconfigure command
	public String UserName;           //The user name sending this message
	public boolean ReturnNewerUpdates;   //Do you want the return message to contain new switch status updates
	//and the new Pending List Updates
	//Only returned if the available update is newer than you represent in the next two inputs
	public int MyCurSMUpdateID;       //The SM Update ID you last received.  
	public int MyCurPendingListUpdateID; //The Pending List ID you last received.  

	private AFOXCmdMsg() { }

	public AFOXCmdMsg(String UserName_in, String Cmd_in, boolean ReturnNewerUpdates_in, int MyCurSMUpdateID_in,
			int MyCurPendingListUpdateID_in) { 
		Cmd = Cmd_in;
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

	public static AFOXCmdMsg DeSerialize(ByteArrayOutputStream ms) {
		AFOXCmdMsg ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXCmdMsg DeSerialize(byte[] b) { 
		AFOXCmdMsg ci = FromFlatSer(b);
		return ci;
	}


	/***************************************************************************
	 *                                                                          *
	 *  Custom Memory Efficient Serializer/Deserialzer                          *
	 *  All variables very flat.  No headers etc.                               *
	 *                                                                          *
	 *  byte[] ToFlatSer() : returns byte array of serialized data              *
	 *                                                                           *  
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
		Object o[] = new Object[6];
		o[0] = Header;
		o[1] = UserName;
		o[2] = Cmd;
		o[3] = ReturnNewerUpdates;
		o[4] = MyCurSMUpdateID;
		o[5] = MyCurPendingListUpdateID;
		o[6] = o;
		return f.serializedData;
	}
	
	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXCmdMsg FromFlatSer(byte[] ba) { 
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 
	
	public static AFOXCmdMsg FromFlatSer(UnflatSerialize uf) { 
		AFOXCmdMsg ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		String UserName_in;
		String Cmd_in;
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
			Cmd_in = uf.stringVal;

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
			success = uf.getNumBytesLeft() == 0; 
			if(!success) break;

			if(success)  //must be success if we made it here
			{ ci = new AFOXCmdMsg(
					UserName_in,
					Cmd_in,
					ReturnNewerUpdates_in,
					MyCurSMUpdateID_in,
					MyCurPendingListUpdateID_in );
			}

			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}


	public int getHeader() {
		return AFOXMsgs.TCP_AFOXCmdMsg; 
	}

}
