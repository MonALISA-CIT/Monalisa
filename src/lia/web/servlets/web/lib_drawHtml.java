package lia.web.servlets.web;

import java.io.PrintWriter;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletResponse;

/**
 */
public class lib_drawHtml {

    /**
     * @param out
     * @param strName
     */
    public static void drawString(PrintWriter out, StringTokenizer strName) {
	while (true) {
	    out.print(strName.nextToken());
	    if (strName.hasMoreTokens()) {
		out.print(" ");
	    } else {
		out.println("");
		break;
	    }
	}
    }

    /********************** drawForm *****************************************  
     * @param out
     * @param action
     */
    public static void drawForm(PrintWriter out, String action) {
	out.println("<form method=POST action=" + action + ">");
    }

    /**
     * ********************** closeForm *****************************************  
     * @param out
     */
    public static void closeForm(PrintWriter out) {
	out.println("</form>");
    }

    /**
     * @param out
     * @param border
     * @param align
     * @param width
     */
    public static void openTable(PrintWriter out, String border,
				 String align, String width) {
	out.println("<br><br><div align=" + align + ">");
	out.println("<table border=" + border + " width='" + width + "'>");
    }

    /**
     * @param out
     */
    public static void closeTable(PrintWriter out) {
	out.println("</table></div><br><br><br>");
    }

    /**
     * @param out
     * @param ObjType
     * @param value
     */
    public static void drawObjects(PrintWriter out, String ObjType,
				   StringTokenizer value) {
	String strValue = "";

	if (ObjType.compareTo("link") == 0) {
	    strValue = value.nextToken();

	    while (strValue.compareTo("end") != 0) {
		out.println("<a href=" + strValue + ">" +
			    value.nextToken() + "</a>");
		strValue = value.nextToken();
	    }
	}

	else if (ObjType.compareTo("button") == 0) {
	    strValue = value.nextToken();
	    while (strValue.compareTo("end") != 0) {
		out.println("<input type=submit value=" + strValue +
			    " name=button>");
		strValue = value.nextToken();
	    }
	}

	else if (ObjType.compareTo("checkbox") == 0) {
	    strValue = value.nextToken();
	    while (strValue.compareTo("end") != 0) {
		out.println("<tr><td>");
		drawString(out, new StringTokenizer(strValue, "_"));
		out.println("</td><td>");
		out.println("<input type=checkbox name=checkbox value=" +
			    strValue + " " + value.nextToken() + ">");
		out.println("</td></tr>");
		strValue = value.nextToken();
	    }
	}

	else if (ObjType.compareTo("editbox") == 0) {
	    String Name = null;
	    strValue = value.nextToken();
	    while (strValue.compareTo("end") != 0) {
		Name = strValue;
		out.println("<tr><td>");
		drawString(out, new StringTokenizer(Name, "_"));
		out.println("</td>");
		strValue = value.nextToken();
		out.println("<td>");
		out.println("<input type=" + strValue + " name=" + Name +
			    " value='' size=20>");
		out.println("</td></tr>");
		strValue = value.nextToken();
	    }
	}

	else if (ObjType.compareTo("editboxDefault") == 0) {
	    strValue = value.nextToken();
	    while (strValue.compareTo("end") != 0) {
		out.println("<tr><td>");
		drawString(out,
			   new StringTokenizer(strValue + "&nbsp&nbsp",
					       "_"));
		out.println("</td><td>");
		out.println("<input type=text name=" + value.nextToken() +
			    " value=" + value.nextToken() + " size=20>");
		out.println("</td></tr>");
		strValue = value.nextToken();
	    }
	}

	else if (ObjType.compareTo("label") == 0) {
	    out.println("<tr>");
	    strValue = value.nextToken();
	    while (strValue.compareTo("end") != 0) {
		out.println("<td>" + strValue + "</td>");
		strValue = value.nextToken();
	    }
	    out.println("</tr>");
	}
    }

    /**
     * @param out
     * @param res
     * @param Title
     */
    public static void drawHeader(PrintWriter out, HttpServletResponse res,
				  String Title){
	res.setContentType("text/html");
	out.println("<html><head><title>" + Title + "</title></head>");
	out.println("<body background='../backusl.jpg'>");
	out.println("<font face='Times New Roman'>");
    } 
    
    /**
     * @param out
     */
    public static void closeHtml(PrintWriter out) {
	out.println("</font></body></html>");
    }

}
