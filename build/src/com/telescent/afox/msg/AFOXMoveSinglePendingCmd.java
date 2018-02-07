package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXMoveSinglePendingCmd implements Serializable, IHeader {

	private static final long serialVersionUID = -6220071098422769480L;

	public int Header = getHeader(); 
	public String UserName;
	public int PendingIDSource;  //The Id of the entry we want to move/delete etc.  
	public int PendingIDDestReference;  //The Id of the entry we to move in front or in back of etc.
	public int Action;  //The action such as move in front or, in back of, to beginning of Q etc.

	private AFOXMoveSinglePendingCmd() { }

	public AFOXMoveSinglePendingCmd(
			String UserName_in,
			int PendingIDSource_in,
			int PendingIDDestReference_in,
			int Action_in) {
		UserName = UserName_in;
		PendingIDSource = PendingIDSource_in;
		PendingIDDestReference = PendingIDDestReference_in;
		Action = Action_in;
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

	public static AFOXMoveSinglePendingCmd DeSerialize(ByteArrayOutputStream ms) {
		AFOXMoveSinglePendingCmd ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXMoveSinglePendingCmd DeSerialize(byte[] b) {
		AFOXMoveSinglePendingCmd ci = FromFlatSer(b);
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
		Object o[] = new Object[5];
		o[0] = Header;
		o[1] = UserName;
		o[2] = PendingIDSource;
		o[3] = PendingIDDestReference;
		o[4] = Action;
		f.AddItems(o);
		return f.serializedData;
	}
	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXMoveSinglePendingCmd FromFlatSer(byte[] ba) {
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 

	public static AFOXMoveSinglePendingCmd FromFlatSer(UnflatSerialize uf) {
		AFOXMoveSinglePendingCmd ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		String UserName_in;
		int PendingIDSource_in;
		int PendingIDDestReference_in;
		int Action_in;
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
			
			success = uf.getInt();     
			if(!success) break;
			PendingIDSource_in = uf.intVal;

			success = uf.getInt();     
			if(!success) break;
			PendingIDDestReference_in = uf.intVal;

			success = uf.getInt();     
			if(!success) break;
			Action_in = uf.intVal;

			//Check we have exactly 0 bytes left or we have an error
			success = uf.getNumBytesLeft() == 0; if(!success) break;

			if(success)  //must be success if we made it here
			{ ci = new AFOXMoveSinglePendingCmd(
					UserName_in,
					PendingIDSource_in,
					PendingIDDestReference_in,
					Action_in
			);
			}

			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}

	public int getHeader() {
		return AFOXMsgs.TCP_AFOXMoveSinglePendingCmd; 
	}
}