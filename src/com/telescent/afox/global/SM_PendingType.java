package com.telescent.afox.global;

import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class SM_PendingType {

	public int PendingID;  //The Pending ID of a command
	public String Command;
	public String TimeEntered;
	public String TimeStarted;
	public String GetTimeEnded;
	public boolean Error;
	public int ErrType;
	public int ErrCode;
	public String ErrMsg;
	public String ConfigCmdStatus;
	public String CmdStatus;
	public boolean Done;
	
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("[SM_PendingType (");
		b.append("PendingID=").append(PendingID).append(" ");
		b.append(")]");
		return b.toString();
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
	public byte[] ToFlatSer()
	{ FlatSerialize f = new FlatSerialize();
	Object [] items = new Object[12];
	items[0] = PendingID;
	items[1] = FlatSerialize.NoNull(Command);
	items[2] = FlatSerialize.NoNull(TimeEntered);
	items[3]= FlatSerialize.NoNull(TimeStarted);
	items[4]= FlatSerialize.NoNull(GetTimeEnded);
	items[5]= Error;
	items[6]= ErrType;
	items[7]= ErrCode ;
	items[8]= FlatSerialize.NoNull(ErrMsg);
	items[9]= FlatSerialize.NoNull(ConfigCmdStatus);
	items[10]= FlatSerialize.NoNull(CmdStatus);
	items[11]= Done;
	f.AddItems(items);

	return f.serializedData;
	}

	//extract and populate structure and return the rest of the byte stream
	public static SM_PendingType FromFlatSerUnique(UnflatSerialize uf) { 
		//si = struct instance
		SM_PendingType si = new SM_PendingType();  //init struct      

		boolean success;
		while(true) { //only done one time.  a "trick" for quick branching out if any step fails.
			success = uf.getInt();			
			if(!success) 
				break;
			si.PendingID = uf.intVal;

			success = uf.getString();        
			if(!success)
				break;
			si.Command = uf.stringVal;

			success = uf.getString(); 
			if(!success)
				break;
			si.TimeEntered = uf.stringVal;

			success = uf.getString();     
			if(!success)
				break;
			si.TimeStarted = uf.stringVal;

			success = uf.getString();       
			if(!success)
				break;
			si.GetTimeEnded = uf.stringVal;

			success = uf.getBoolean();              
			if(!success)
				break;
			si.Error = uf.boolVal;

			success = uf.getInt();             
			if(!success) 
				break;
			si.ErrType = uf.intVal;

			success = uf.getInt();              
			if(!success)
				break;
			si.ErrCode = uf.intVal;

			success = uf.getString();               
			if(!success)
				break;
			si.ErrMsg = uf.stringVal;

			success = uf.getString();     
			if(!success)
				break;
			si.ConfigCmdStatus = uf.stringVal;

			success = uf.getString();          
			if(!success)
				break;
			si.CmdStatus = uf.stringVal;

			success = uf.getBoolean();                
			if(!success)
				break;
			si.Done = uf.boolVal;
			break;  //always finish with break so we only do the while loop once.
		}
		if(!success) si = new SM_PendingType();  //If failure, reinit structure
		return si;
	}

	//deserialize this in a 1-dim Array
	public static SM_PendingType[] FromFlatSer(UnflatSerialize uf) { 
		int i_len = 0;
		SM_PendingType[] Arr = null; //init to null
		boolean success;

		try { 
			success = uf.getInt();
			if(!success) return null;
			i_len = uf.intVal;
			
			Arr = new SM_PendingType[i_len];
			for(int i=0; i<i_len; i++) { 
				Arr[i] = FromFlatSerUnique(uf);
			}
			return Arr;  //should be successful if here
		} catch(Exception e) {  
			return null;
		}
	}


	//Serialize this in a 1-dim Array
	public static byte[] ToFlatSer(SM_PendingType[] Arr) { 
		FlatSerialize f = new FlatSerialize();
		int i_len;

		i_len = Arr.length;
		Object[] items = new Object[1];
		items[0] = i_len;
		f.AddItems(items);
		for(int i=0; i<i_len; i++)
		{ f.AddSerData(Arr[i].ToFlatSer());
		}
		return f.serializedData;
	}

}
