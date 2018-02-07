/**
 * 
 */
package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.Color;
import java.text.NumberFormat;

/**
 * @author florinpop
 *
 */
public class OSGToolTip {

	StringBuilder sb;
	String bgcolor = "#FFFFFF";
	String sbgcolor = "#FAFAFA";
	public boolean changeBg = false;
	
	public OSGToolTip(){
		sb = new StringBuilder();
	}
	
	public OSGToolTip(String color){
		if(color.length()==7 && color.startsWith("#"))
			bgcolor = color;
		sb = new StringBuilder();
	}
	
	public String getToolTip(String farmName){
		StringBuilder new_sb = new StringBuilder();
		
		new_sb.append("<html>");
		new_sb.append("<body bgcolor=\""+bgcolor+"\">");
		new_sb.append("<div align=center><b>" + farmName + "</b></div>");
		new_sb.append("<table BORDER=0 size=\"150\">");
		
		new_sb.append(sb);
		
		new_sb.append("</table>");
		new_sb.append("</body>");
		new_sb.append("</html>");
		
		return new_sb.toString();
	}
	
	public String getToolTip(){
		StringBuilder new_sb = new StringBuilder();
		
		new_sb.append("<html>");
		new_sb.append("<body bgcolor=\""+bgcolor+"\">");
		new_sb.append("<table BORDER=0 size=\"150\">");
		
		new_sb.append(sb);
		
		new_sb.append("</table>");
		new_sb.append("</body>");
		new_sb.append("</html>");
		
		return new_sb.toString();
	}
	
	public void addEntry(String name, Color color, NumberFormat nf, double value){
		
		if(changeBg)
			sb.append("<tr bgcolor=\""+sbgcolor+"\">");
		else 
			sb.append("<tr bgcolor=\""+bgcolor+"\">");
		
		sb.append("<td>");
		sb.append("<table><tr><td bgcolor=\"").append(OSGColor.decodeColor(color)).append("\"></td></tr></table>");
		sb.append("</td>");
		
		sb.append("<td align=\"left\">");
		sb.append("<font face=\"arial\">"+name+"</font>");
		sb.append("</td>");
		
		sb.append("<td align=\"right\">");
		sb.append(nf.format(value));
		sb.append("</td>");
		
		sb.append("</tr>");
		
	}
	
	public void addEntry(String name, Color color, NumberFormat nf, double value, double p){

		NumberFormat df = NumberFormat.getInstance();
		df.setMinimumFractionDigits(2);
		df.setMaximumFractionDigits(2);
		
		if(changeBg)
			sb.append("<tr bgcolor=\""+sbgcolor+"\">");
		else 
			sb.append("<tr bgcolor=\""+bgcolor+"\">");
		
		sb.append("<td>");
		sb.append("<table><tr><td bgcolor=\"").append(OSGColor.decodeColor(color)).append("\"></td></tr></table>");
		sb.append("</td>");
		
		sb.append("<td align=\"left\">");
		sb.append("<font face=\"arial\">"+name+"</font>");
		sb.append("</td>");
		
		sb.append("<td align=\"right\">");
		sb.append(nf.format(value));
		sb.append("</td>");
		
		sb.append("<td align=\"right\"> (" + df.format(p) + "%) </td>");
		
		sb.append("</tr>");
		
	}
	
	public void changeBg(){
		changeBg = !changeBg;
	}

}
