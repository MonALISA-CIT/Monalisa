package com.telescent.afox.global;
import com.telescent.afox.utils.FlatSerialize;
import com.telescent.afox.utils.UnflatSerialize;

public class SM_InOrOut_CurAndPending 
{
	public int ConnectorNum;  //Input or Output connector number
	public int Row;
	public int Col; 
	public String AFOXName;  //"Internal" AFOX name that never changes
	public String CustomerName;  //Customer assignable name to this connector
	public SMConnectedTo Current; //The connector information for the connector that this connector is connected to currently
	public boolean PendingExists;  //Are there any pending commands
	public SMConnectedTo Pending; //The connector information for the connector that this connector  will be connecto to when all commands are complete.

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("SM_InOrOut_CurAndPending[ ");
		b.append("ConnectorNum=").append(ConnectorNum);
		b.append(" Current=").append(Current);
		b.append("]");
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
	public byte[] ToFlatSer() { 
		FlatSerialize f = new FlatSerialize();
		Object [] items = new Object[5];
		items[0]=ConnectorNum; // 26
		items[1]=Row; // 30
		items[2]=Col; // 34
		items[3]=FlatSerialize.NoNull(AFOXName); // 52
		items[4]=FlatSerialize.NoNull(CustomerName); // 59
		f.AddItems(items);
		f.AddSerData(Current.FlatSer());

		items = new Object[1];
		items[0]=PendingExists;   	
		f.AddItems(items);
		
		f.AddSerData(Pending.FlatSer());
		return f.serializedData;
	}

	//extract and populate structure and return the rest of the byte stream
	public static SM_InOrOut_CurAndPending FromFlatSerUnique(UnflatSerialize uf) 	{ //si = struct instance
		SM_InOrOut_CurAndPending si = new SM_InOrOut_CurAndPending();  //init struct      

		boolean success;
		while(true) { //only done one time.  a "trick" for quick branching out if any step fails.
			success = uf.getInt();                  
			if(!success) break;
			si.ConnectorNum = uf.intVal;

			success = uf.getInt();                          
			if(!success) break;
			si.Row = uf.intVal;

			success = uf.getInt();                            
			if(!success) break;
			si.Col = uf.intVal;

			success = uf.getString();                      
			if(!success) break;
			si.AFOXName = uf.stringVal;

			success = uf.getString();                   
			if(!success) break;
			si.CustomerName = uf.stringVal;

			si.Current = SMConnectedTo.FromFlatSer(uf);     
			if(si.Current == null) break;

			success = uf.getBoolean();                 
			if(!success) break;
			si.PendingExists = uf.boolVal;

			si.Pending = SMConnectedTo.FromFlatSer(uf);     
			if(si.Pending == null) break;

			break;  //always finish with break so we only do the while loop once.
		}
		if(!success) si = new SM_InOrOut_CurAndPending();  //If failure, reinit structure
		return si;
	}

	//deserialize this in a 2-dim Array
	public static SM_InOrOut_CurAndPending[][] FromFlatSer(UnflatSerialize uf) { 
		int i_len;
		int j_len;
		boolean success;

		try { 
			success = uf.getInt();
			if(!success) return null;
			i_len = uf.intVal;

			success = uf.getInt();
			if(!success) return null;
			j_len = uf.intVal;

			SM_InOrOut_CurAndPending[][] Arr = new SM_InOrOut_CurAndPending[i_len][j_len];
			for(int i=0; i<i_len; i++) { 
				for(int j=0; j<j_len; j++) {  
					Arr[i][j] = FromFlatSerUnique(uf);
//					if (Arr[i][j] == null) System.err.println("error");
				}
			}
			return Arr;  //should be successful if here
		} catch(Exception e) { 
			e.printStackTrace();
			return null;
		}
	}

	//Serialize this in a 2-dim Array
	public static byte[] ToFlatSer(SM_InOrOut_CurAndPending[][] Arr) { 
		FlatSerialize f = new FlatSerialize();
		int i_len;
		int j_len;

		i_len = Arr.length; 
		if(i_len > 0)
			j_len = Arr[0].length;
		else
			j_len = 0;
		Object [] items = new Object[2];
		items[0] = i_len; // 18
		items[1] = j_len; // 22
		f.AddItems(items);
		for(int i=0; i<i_len; i++) { 
			for(int j=0; j<j_len; j++)
				f.AddSerData(Arr[i][j].ToFlatSer());
		}
		return f.serializedData;
	}


}
