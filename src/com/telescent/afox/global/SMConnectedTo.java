package com.telescent.afox.global;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class SMConnectedTo {
	public String Status; //Allocation status
	public String ByUser; //The last user to cause this current state or pending state  
	public boolean Allocated; //Allocation status  (retundant for now)
	public String ConnectedToAFOXName;  //AFOX Name of the connector to which this connector is connected.
	public String ConnectedToCustomerName;//Customer Name of the connector to which this connector is connected.
	public int ConnectedToConnectorNum;//Number the connector to which this connector is connected.
	public int ConnectedToRow; //Row number of the connected connector
	public int ConnectedToCol; //Column number of the connected connector

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("SMCOnnectedTo[ ");
		b.append("Status=").append(Status);
		b.append(" Allocated=").append(Allocated);
        b.append(" ConnectedToConnectorNum=").append(ConnectedToConnectorNum);
        b.append(" ConnectedToAFOX=").append(ConnectedToAFOXName);
		b.append(" ConnectedToRow=").append(ConnectedToRow);
		b.append(" ConnectedToCol=").append(ConnectedToCol);		
		b.append("]");
		return b.toString();
	}
	
	public SMConnectedTo(int dummy) { //trick to force a constructor for the struct
		Status = "";
		ByUser = "";
		Allocated = false;
		ConnectedToAFOXName = "";
		ConnectedToCustomerName = "";
		ConnectedToConnectorNum = -1;
		ConnectedToRow = -1;
		ConnectedToCol = -1;
	}

	private SMConnectedTo() {
		// TODO Auto-generated constructor stub
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
	public byte[] FlatSer() { 
		FlatSerialize f = new FlatSerialize();
		Object [] items = new Object[8];
		items[0]=FlatSerialize.NoNull(Status);
		items[1]=FlatSerialize.NoNull(ByUser);
		items[2]=Allocated;
		items[3]=FlatSerialize.NoNull(ConnectedToAFOXName);
		items[4]= FlatSerialize.NoNull(ConnectedToCustomerName);
		items[5]= ConnectedToConnectorNum;
		items[6]= ConnectedToRow;
		items[7]= ConnectedToCol;
		f.AddItems(items);
		return f.serializedData;
	}


	//extract and populate structure and return the rest of the byte stream
	public static SMConnectedTo FromFlatSer(UnflatSerialize uf) { //si = struct instance
		SMConnectedTo si = new SMConnectedTo();  //init struct      

		boolean success;
		while(true) { //only done one time.  a "trick" for quick branching out if any step fails.
			success = uf.getString();            
			if(!success) break;
			si.Status = uf.stringVal;

			success = uf.getString();                 
			if(!success) break;
			si.ByUser = uf.stringVal;

			success = uf.getBoolean(); 
			if(!success) break;
			si.Allocated = uf.boolVal;

			success = uf.getString();   
			if(!success) break;
			si.ConnectedToAFOXName = uf.stringVal;

			success = uf.getString();
			if(!success) break;
			si.ConnectedToCustomerName = uf.stringVal;

			success = uf.getInt(); 
			if(!success) break;
			si.ConnectedToConnectorNum = uf.intVal;

			success = uf.getInt();         
			if(!success) break;
			si.ConnectedToRow = uf.intVal;

			success = uf.getInt();        
			if(!success) break;
			si.ConnectedToCol = uf.intVal;

			break;  //always finish with break so we only do the while loop once.
		}
		if(!success) si = null;  //If failure, reinit structure
		return si;
	}
}
