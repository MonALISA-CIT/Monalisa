package lia.Monitor.JiniClient.CommonGUI.Jogl;

import lia.util.dataStruct.DoubleLinkedListNode;

public class JoGLCachedTexture extends DoubleLinkedListNode {
	String resultID; // treeID + path
	int joglID;		 // jogl-generated texture id
	
	public JoGLCachedTexture(String resultID, int joglID){
		this.resultID = resultID;
		this.joglID = joglID;
	}	
}
