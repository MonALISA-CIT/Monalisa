package com.telescent.afox.msg;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import com.telescent.afox.global.SM_InOrOut_CurAndPending;
import com.telescent.afox.global.SM_PendingType;
import com.telescent.afox.utils.AFOXMsgs;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class AFOXMultiCmdRetMsg implements Serializable, IHeader { 

	private static final long serialVersionUID = -2230963031718537837L;
	
	public int Header = getHeader(); 
	public boolean NewSMUpdateAvailable; 
	public int LatestSMUpdateID; 
	public boolean NewPendingUpdateAvailable; 
	public int LatestPendingUpdateID; 
	public int[] PendingCmdID; //The Pending Cmd IDs in an array just assigned to the command that was just sent in AFOXCmdMsg
	public SM_InOrOut_CurAndPending[][] SMCurrentIns;
	public SM_InOrOut_CurAndPending[][] SMCurrentOuts;
	public SM_PendingType[] SM_PendingList;
	public int FirstPendingIdx;
	public ErrPkt errPkt;

	private AFOXMultiCmdRetMsg() { }

	public AFOXMultiCmdRetMsg(
			boolean NewSMUpdateAvailable_in,
			int LatestSMUpdateID_in,
			boolean NewPendingUpdateAvailable_in,
			int LatestPendingUpdateID_in,
			int[] PendingCmdID_in,
			SM_InOrOut_CurAndPending[][] SMCurrentIns_in,
			SM_InOrOut_CurAndPending[][] SMCurrentOuts_in,
			SM_PendingType[] SM_PendingList_in,
			int FirstPendingIdx_in,
			ErrPkt ErrPkt_in) {
		NewSMUpdateAvailable = NewSMUpdateAvailable_in;
		LatestSMUpdateID = LatestSMUpdateID_in;
		NewPendingUpdateAvailable = NewPendingUpdateAvailable_in;
		LatestPendingUpdateID = LatestPendingUpdateID_in;
		PendingCmdID = PendingCmdID_in;
		SMCurrentIns = SMCurrentIns_in;
		SMCurrentOuts = SMCurrentOuts_in;
		SM_PendingList = SM_PendingList_in;
		FirstPendingIdx = FirstPendingIdx_in;
		errPkt = ErrPkt_in;
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

	public static AFOXMultiCmdRetMsg DeSerialize(ByteArrayOutputStream ms) {
		AFOXMultiCmdRetMsg ci = FromFlatSer(ms.toByteArray());
		return ci;
	}

	public static AFOXMultiCmdRetMsg DeSerialize(byte[] b) {
		AFOXMultiCmdRetMsg ci = FromFlatSer(b);
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
		Object o[] = new Object[6];
		o[0] = Header;
		o[1] = NewSMUpdateAvailable;
		o[2] = LatestSMUpdateID;
		o[3] = NewPendingUpdateAvailable;
		o[4] = LatestPendingUpdateID;
		o[5] = PendingCmdID;
		f.AddItems(o);
		f.AddSerData(SM_InOrOut_CurAndPending.ToFlatSer(SMCurrentIns));
		f.AddSerData(SM_InOrOut_CurAndPending.ToFlatSer(SMCurrentOuts));
		f.AddSerData(SM_PendingType.ToFlatSer(SM_PendingList));
		o = new Object[1];
		o[0] = FirstPendingIdx;
		f.AddItems(o);
		f.AddSerData(errPkt.ToFlatSer());
		return f.serializedData;
	}

	//Deserialzer
	//extract and populate structure and return the rest of the byte stream
	public static AFOXMultiCmdRetMsg FromFlatSer(byte[] ba) {
		UnflatSerialize uf = new UnflatSerialize(ba);
		return(FromFlatSer(uf));
	} 

	public static AFOXMultiCmdRetMsg FromFlatSer(UnflatSerialize uf) {
		AFOXMultiCmdRetMsg ci = null;  // ci = class instance.  init to null in case of error later so we have our, (defalut-error), return value.

		/* 1)
		 * copy the constructor variables here.  This class is entirely populated by
		 * the constructor, and we will instantiate an instance here, (to return to the
		 * caller) by using the inputs just below that will be populated as uf below
		 * is deserialized into them.
		 */
		boolean NewSMUpdateAvailable_in;
		int LatestSMUpdateID_in;
		boolean NewPendingUpdateAvailable_in;
		int LatestPendingUpdateID_in;
		int[] PendingCmdID_in;
		SM_InOrOut_CurAndPending[][] SMCurrentIns_in;
		SM_InOrOut_CurAndPending[][] SMCurrentOuts_in;
		SM_PendingType[] SM_PendingList_in;
		int FirstPendingIdx_in;
		ErrPkt ErrPkt_in;
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
			NewSMUpdateAvailable_in = uf.boolVal;

			success = uf.getInt();     
			if(!success) break;
			LatestSMUpdateID_in = uf.intVal;

			success = uf.getBoolean();     
			if(!success) break;
			NewPendingUpdateAvailable_in = uf.boolVal;

			success = uf.getInt();
			if(!success) break;
			LatestPendingUpdateID_in = uf.intVal;

			success = uf.getIntArray();
			if(!success) break;
			PendingCmdID_in = uf.intArray;
			
			SMCurrentIns_in = SM_InOrOut_CurAndPending.FromFlatSer(uf);  if(SMCurrentIns_in == null) break;
			SMCurrentOuts_in = SM_InOrOut_CurAndPending.FromFlatSer(uf);  if(SMCurrentOuts_in == null) break;
			SM_PendingList_in = SM_PendingType.FromFlatSer(uf);  if(SM_PendingList_in == null) break;
			
			success = uf.getInt();
			if(!success) break;
			FirstPendingIdx_in = uf.intVal;

			ErrPkt_in = new ErrPkt();
			success = ErrPkt.FromFlatSer(uf, ErrPkt_in); if(!success) break;

			//Check we have exactly 0 bytes left or we have an error
			success = uf.getNumBytesLeft() == 0; if(!success) break;

			if(success)  //must be success if we made it here
			{ ci = new AFOXMultiCmdRetMsg(
					NewSMUpdateAvailable_in,
					LatestSMUpdateID_in,
					NewPendingUpdateAvailable_in,
					LatestPendingUpdateID_in,
					PendingCmdID_in,
					SMCurrentIns_in,
					SMCurrentOuts_in,
					SM_PendingList_in,
					FirstPendingIdx_in,
					ErrPkt_in
			);
			}

			break;  //always finish with break so we only do the while loop once.

		}
		return ci;
	}

	public int getHeader() {
		return AFOXMsgs.TCP_AFOXMultiCmdRetMsg; 
	}
}