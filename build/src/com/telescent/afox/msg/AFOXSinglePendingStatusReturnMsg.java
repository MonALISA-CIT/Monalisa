package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.global.SM_PendingType;
import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXSinglePendingStatusReturnMsg implements Serializable, IHeader { 

	private static final long serialVersionUID = 4132192265816004240L;

	public int Header = getHeader();  
	public boolean StatusAvailable; 
	public SM_PendingType PendingStatus;

	private AFOXSinglePendingStatusReturnMsg() { }

	public AFOXSinglePendingStatusReturnMsg(
			boolean StatusAvailable_in,
			SM_PendingType PendingStatus_in) {
		StatusAvailable = StatusAvailable_in;
		PendingStatus = PendingStatus_in;
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

	public static AFOXSinglePendingStatusReturnMsg DeSerialize(ByteArrayOutputStream ms) {
		AFOXSinglePendingStatusReturnMsg ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXSinglePendingStatusReturnMsg DeSerialize(byte[] b) {
		AFOXSinglePendingStatusReturnMsg ci = FromFlatSer(b);
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
		Object o[] = new Object[2];
		o[0] = Header;
		o[1] = StatusAvailable;
		f.AddItems(o);
		f.AddSerData(PendingStatus.ToFlatSer());
		return f.serializedData;
	}

	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXSinglePendingStatusReturnMsg FromFlatSer(byte[] ba) {
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 

	public static AFOXSinglePendingStatusReturnMsg FromFlatSer(UnflatSerialize uf) {
		AFOXSinglePendingStatusReturnMsg ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		boolean StatusAvailable_in;
		SM_PendingType PendingStatus_in;
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
			
			success = uf.getBoolean();     
			if(!success) break;
			StatusAvailable_in = uf.boolVal;

			PendingStatus_in = null;
			PendingStatus_in = SM_PendingType.FromFlatSerUnique(uf);  
			if(PendingStatus_in == null) break;
			//Check we have exactly 0 bytes left or we have an error
			success = uf.getNumBytesLeft() == 0; 
			if(!success) 
				break;

			ci = new AFOXSinglePendingStatusReturnMsg(
					StatusAvailable_in,
					PendingStatus_in );
			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}


	public int getHeader() {
		return AFOXMsgs.TCP_AFOXSinglePendingStatusReturnMsg;
	}
}
