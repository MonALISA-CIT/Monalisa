package com.telescent.afox.global;

import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class SystemStatus {

	public boolean PendingShutdown;   //System will shutdown after completion of current command
	public String ShutdownByUserName;  //User who requested shutdown
	public boolean PendingQPause;     //If true, the system will pause the pending commands Q after current command completion
	public boolean QPaused;   //Is the Q currently paused
	public String QPauseByUserName;  //Who paused the Q
	public String QStartedByUserName;  //Who started the Q
	public boolean SMInProgress;  //SM is busy physically moving, (in the middle of a reconfiguration)
	public boolean SMLocalControlOnly;  //SM is local control only, possibly an error requiring local service has occurred.  API cannot be used.
	public boolean DBandStatesSynched;  //Tells whether or not the DB and the States file were synched.  If not synched, the API cannot be used.

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
		Object [] items = new Object[9];
		items[0]=PendingShutdown;
		items[1]=FlatSerialize.NoNull(ShutdownByUserName);
		items[2]= PendingQPause;
		items[3]= QPaused;
		items[4]= FlatSerialize.NoNull(QPauseByUserName);
		items[5]= FlatSerialize.NoNull(QStartedByUserName);
		items[6]= SMInProgress;
		items[7]= SMLocalControlOnly;
		items[8]=  DBandStatesSynched;
		f.AddItems(items);
		return f.serializedData;
	}

	//extract and populate structure and return the rest of the byte stream
	public static boolean FromFlatSer(UnflatSerialize uf, SystemStatus si)
	{ //si = struct instance
		if (si == null)
			si = new SystemStatus();  //init struct      

		boolean success;
		while(true) //only done one time.  a "trick" for quick branching out if any step fails.
		{
			success = uf.getBoolean();     
			if(!success) break;
			si.PendingShutdown = uf.boolVal;

			success = uf.getString();  
			if(!success) break;
			si.ShutdownByUserName = uf.stringVal;

			success = uf.getBoolean();       
			if(!success) break;
			si.PendingQPause = uf.boolVal;

			success = uf.getBoolean();            
			if(!success) break;
			si.QPaused = uf.boolVal;

			success = uf.getString();   
			if(!success) break;
			si.QPauseByUserName = uf.stringVal;

			success = uf.getString();  
			if(!success) break;
			si.QStartedByUserName = uf.stringVal;

			success = uf.getBoolean();       
			if(!success) break;
			si.SMInProgress = uf.boolVal;

			success = uf.getBoolean();  
			if(!success) break;
			si.SMLocalControlOnly = uf.boolVal;
			
			success = uf.getBoolean(); 
			if(!success) break;
			si.DBandStatesSynched = uf.boolVal;

			break;  //always finish with break so we only do the while loop once.
		}
		if(!success) si = new SystemStatus();  //If failure, reinit structure
		return success;

	}
}
