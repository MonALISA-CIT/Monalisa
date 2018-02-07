package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.global.SystemStatus;
import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXAckMsg implements Serializable, IHeader {

	private static final long serialVersionUID = -8890589857417387470L;

	public int Header = getHeader(); 
	public int Contents_ID;  //ID to signal what is in the AckMsg
	public String UserName;
	public boolean AckStatus;
	public byte[] ByteArray;  //Could contain anything depending on context
	public SystemStatus systemStatus;  //See structure. Wouldn't be used if client was sending this to server.
	public int int1;  //Possible useful integer value depending on context
	public int int2;  //Possible useful integer value depending on context
	public ErrPkt errPkt;

	private AFOXAckMsg() { }

	public AFOXAckMsg(
			int Contents_ID_in,
			String UserName_in,
			boolean AckStatus_in,
			byte[] ByteArray_in,  //Could contain anything depending on context
			SystemStatus SystemStatus_in,
			int int1_in,
			int int2_in,
			ErrPkt ErrPkt_in) {
		Contents_ID = Contents_ID_in;
		UserName = UserName_in;
		AckStatus = AckStatus_in;
		ByteArray = ByteArray_in;
		systemStatus = SystemStatus_in;
		int1 = int1_in;
		int2 = int2_in;
		errPkt = ErrPkt_in;
	}

	//Constructor for System Status
	public AFOXAckMsg(boolean AckStatus_in,
			SystemStatus SystemStatus_in) {
		Contents_ID = AFOXMsgs.ACK_ID_GENERAL;
		UserName = "";
		AckStatus = AckStatus_in;
		ByteArray = new byte[0];
		systemStatus = SystemStatus_in;
		int1 = 0;
		int2 = 0;
		errPkt = new ErrPkt();
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

	public static AFOXAckMsg DeSerialize(ByteArrayOutputStream ms) {
		AFOXAckMsg ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXAckMsg DeSerialize(byte[] b) {
		AFOXAckMsg ci = FromFlatSer(b);
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
		o[1] = Contents_ID;
		o[2] = UserName;
		o[3] = AckStatus;
		o[4] = ByteArray;
		f.AddItems(o);
		f.AddSerData(systemStatus.ToFlatSer());
		o = new Object[2];
		o[0] = int1;
		o[1] = int2;
		f.AddItems(o);
		f.AddSerData(errPkt.ToFlatSer());
		return f.serializedData;
	}

	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXAckMsg FromFlatSer(byte[] ba) {
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 

	public static AFOXAckMsg FromFlatSer(UnflatSerialize uf) {
		AFOXAckMsg ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		int Contents_ID_in;
		String UserName_in;
		boolean AckStatus_in;
		byte[] ByteArray_in;
		SystemStatus SystemStatus_in;
		int int1_in;
		int int2_in;
		ErrPkt ErrPkt_in;
		/* 2) get each item, (deserialize each item), in the proper order, (the same way 
		 * they were serialized).
		 * Check each "get" for success or failure.  If it fails, abondon the
		 * the rest of the deserialization and return an error.
		 */

		boolean success;
		while(true) {//while loop only done one time.  a "trick" for quick branching out if any step fails.
			int headerDummy;  //Need to read header, but we don't use it later to instantiate.
			
			success = uf.getInt();     
			if(!success) break;
			headerDummy = uf.intVal;
			
			success = uf.getInt();     
			if(!success) break;
			Contents_ID_in = uf.intVal;

			success = uf.getString();     
			if(!success) break;
			UserName_in = uf.stringVal;

			success = uf.getBoolean();     
			if(!success) break;
			AckStatus_in = uf.boolVal;

			success = uf.getByteArray();
			if(!success) break;
			ByteArray_in = uf.byteArray;

			SystemStatus_in = null;
			success = SystemStatus.FromFlatSer(uf, SystemStatus_in);  if(!success) break;
			
			success = uf.getInt();     
			if(!success) break;
			int1_in = uf.intVal;

			success = uf.getInt();     
			if(!success) break;
			int2_in = uf.intVal;

			ErrPkt_in = null;
			success = ErrPkt.FromFlatSer(uf, ErrPkt_in); if(!success) break;

			//Check we have exactly 0 bytes left or we have an error
			success = uf.getNumBytesLeft() == 0; if(!success) break;

			if(success)  //must be success if we made it here
			{ ci = new AFOXAckMsg(
					Contents_ID_in,
					UserName_in,
					AckStatus_in,
					ByteArray_in,
					SystemStatus_in,
					int1_in,
					int2_in,
					ErrPkt_in
			);
			}

			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}

	public int getHeader() {
		return AFOXMsgs.TCP_AFOXAckMsg; 
	}

}