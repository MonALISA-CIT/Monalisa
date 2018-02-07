/**
 * 
 */
package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.Color;

/**
 * @author florinpop
 *
 */
public class ParamProperties {
	
	public String name;
	public Color color;
	
	public ParamProperties(String name, Color color){
		this.name = name;
		this.color = color;
	}
	
	public void setName(String name){
		this.name = name; 
	}
	
	public void setColor(Color color){
		this.color = color; 
	}
}
