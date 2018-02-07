package lia.Monitor.Agents.SchedAgents;

import java.io.Serializable;

public class UserRequest implements Serializable {
    
	private static final long serialVersionUID = 1699028120361910782L;
	static int idCnt = 0;
	
	protected int ID;
    
    public UserRequest() {
    	this.ID = idCnt++;
    }
	
	/*
	public UserRequest(String commandName, int minMemory, int maxMemory) {
		this.ID = idCnt++;
		this.commandName = commandName;
		this.minMemory = minMemory;
		this.maxMemory = maxMemory;
	}
	
	public UserRequest(String spec) {
		String tok;
		StringTokenizer st = new StringTokenizer(spec, ";");
		
		tok = st.nextToken();
		String idS = tok.replaceFirst("id: ", "");
		this.ID = Integer.parseInt(idS);
				
		tok = st.nextToken();
		this.commandName = tok.replaceFirst(" commandName: ", "");
		
		tok = st.nextToken();
		String minMemS = tok.replaceFirst(" minMem: ", "");
		this.minMemory = Integer.parseInt(minMemS);
		
		System.out.println("### Constructor ready " + this.minMemory);
	}
	*/
	public int getID() {
		return ID;
		
	}
    /*
	public String toString() {
		return ("id: " + ID + "; commandName: " + commandName + 
				"; minMem: " + minMemory);	}
	*/
}
