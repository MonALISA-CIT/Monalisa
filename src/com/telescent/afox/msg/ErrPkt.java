package com.telescent.afox.msg;

import java.io.Serializable;

import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

/***************************************************************************
 *                                                                          *
 *  Serializable Struct                                                     *
 *                                                                          *
 *                                                                          *
 ***************************************************************************/


public class ErrPkt implements Serializable { 

	private static final long serialVersionUID = 7838921002124004347L;
	
	public String ID = "";
	public boolean HasErr;
	public String CallingFunction = "";
	public String Date = "";
	public String ErrStr = "";
	public int ErrCatagory;  //meaning TBD
	public int ErrCode; //meaning TBD
	public int ErrType; //meaning TBD

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
		Object o[] = new Object[8];
		o[0] = ID;
		o[1] = HasErr;
		o[2] = FlatSerialize.NoNull(CallingFunction);
		o[3] = Date;
		o[4] = ErrStr;
		o[5] = ErrCatagory;
		o[6] = ErrCode;
		o[7] = ErrType;
		f.AddItems(o);
		return f.serializedData;
	}
	
	//extract and populate structure and return the rest of the byte stream
	public static boolean FromFlatSer(UnflatSerialize uf, ErrPkt si) { 
		//si = struct instance
		if (si == null)
			si = new ErrPkt();  //init struct      

		boolean success;
		while(true) {//only done one time.  a "trick" for quick branching out if any step fails.
			
			success = uf.getString();     
			if(!success) break;
			si.ID = uf.stringVal;
			
			success = uf.getBoolean();     
			if(!success) break;
			si.HasErr = uf.boolVal;
			
			success = uf.getString();     
			if(!success) break;
			si.CallingFunction = uf.stringVal;
			
			success = uf.getString();     
			if(!success) break;
			si.Date = uf.stringVal;

			success = uf.getString();     
			if(!success) break;
			si.ErrStr = uf.stringVal;
			
			success = uf.getInt();     
			if(!success) break;
			si.ErrCatagory = uf.intVal;

			success = uf.getInt();     
			if(!success) break;
			si.ErrCode = uf.intVal;
			
			success = uf.getInt();     
			if(!success) break;
			si.ErrType = uf.intVal;
			break;  //always finish with break so we only do the while loop once.
		}

		if(!success) si = new ErrPkt();  //If failure, reinit structure
		return success;
	}

}