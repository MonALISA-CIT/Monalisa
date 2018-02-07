package lia.web.servlets.monsit;

/**
 * @version 1.3.1
 * @author mluc
 * Servlet that constructs a dynamic content for a site based on a files and directories structure.<br>
 * It also provides different level of security for accessing each file/directory: based on email or
 * on registered user).
 * 
 * ChangeLog:
 * 1.2 -> hopefully repaired an Out Of Memory Exception by clearing the Variables and Users hashtables
 * before loading new information into them
 * 		 -> no content when sign up page
 *
 * 1.2.1 -> repaired link address generation
 *       -> interpret sign-up link in every page
 * 
 * 1.2.2 -> authenticate only by email
 * 	     -> added NewsTitle variable
 * 	     -> news directory can be put anywhere inside content dir, but with the same structure as before
 * 	     -> can be unlimited number of news directories
 * 	     -> news file template is taken from news directory
 * 	     -> sort options in menu by using .order file that contains a list of file names
 *       -> ...
 *       
 * 1.2.3 -> login supported by user and password or email, only user, or only email (the last one is obtained by specifying "o" as
 * access parameter)
 *         -> small update for news icon to be different from ordinary files (a feature introduced several versions ago, but
 * accidentally removed at last update)
 * 
 * 1.2.4 -> resolved buffer problem at downloading a file: extra padding added to a file to be a multiple of 1024.
 * 
 * 1.2.5 -> set default download file type as application/octet-stream 
 *         -> add header for file length
 * 1.2.6 -> added customization for menu options in the form of a .properties file
 * that can replace the .order file and gives the following attributes:
 *      - to a directory: icon, icon_open and icon_open_sel, title, nice, class 
 *      and class_sel, and also order and name
 *      - to a file: nice (name the option will appear with), icon (icon in menu),
 *      icon_open_sel (icon in menu for when selected), title (alternate text for 
 *      when the mouse is on link), class (special class for option, if it is not 
 *      currently selected), class_sel (class for selected option), target (specify 
 *      new target if neccessary for link), order (can replace the .order file by 
 *      specifying a integer number, positive or negative).
 * 1.2.7 -> added automatic validation of any link if AnyEmailIsValid variable is
 * 		set to true in variables file
 * 		-> moved the checking of .order and .properties files in separate functions
 * 		and enabled ordering of the news not only based on access date for files
 * 		but also on .order file in news directory.
 * 		-> build.xml constructs two jars, one with aditional classes needed, and
 * 		one with only the monsit servlet for an easy update.
 * 1.2.8 -> added 4 special variables: monsitVersion, monsitRequests,
 * 		monsitUptime and monsitDate that describe this servlet and its
 * 		status.
 * 		-> Unified the three messages in only one that displays the name
 * 		of the requested page and the execution time.
 * 1.2.9 -> exposes two more variables to show the number of requests
 * 		between two restarts of the server: monsitFirstRunTime and
 * 		monsitTotalRequests
 * 1.2.9.1 -> modiffied output for UpTime
 * 1.2.9.2 -> modiffied output for UpTime, removed '0' prefix
 * 1.3 -> important change in how the downloadable files are served
 *        by the servlet; it doesn't serve a file himself, but only
 *        redirects to apache.
 * 1.3.1 -> changed root path from tomcat to apache
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import lia.util.mail.DirectMailSender;
import lia.util.ntp.NTPDate;
import lia.web.utils.MailDate;
import lia.web.utils.Page;
import lia.web.utils.ThreadedPage;

public class monsit extends ThreadedPage {

	private static final long serialVersionUID = -4397833170556473479L;
	//sync object for incrementing confirmations counter
	public static Object objConfSync = new Object();
	public static int nConfirmationsCounter = 1;
	//sync object for modifying/accessing valid_email file
	public static Object objVEaccesSync = new Object();
	//sync object for modifying/accessing confirmations file
	public static Object objCaccesSync = new Object();

	String sResDir = "";
	String sConfDir = "";
	String sContentDir = "";
	String sLogDir = "";
	//String sNewsDir = "";
	String sServletName = "";
	String sWebPath = "";
	String sWebResDir = "";
	String sResExt = ".htm";
	String sConfExt = ".conf";
	String sLogFile = "";
	
	boolean bOrderDesc = true;
	
	HashMap hVariables = new HashMap();//hash map containing the values to change the page
	HashMap hUsers = new HashMap();//hash map with user_id -> String[]={user,password,email,name}
	
	Page pMaster;

	public static final String getUptime(){
		long l = NTPDate.currentTimeMillis() - lRepositoryStarted;
		l/=1000;
		//long s = l%60; 
		l/=60;
		long m = l%60; l/=60;
		long h = l%24; l/=24;
		long d = l;
		return d+" day"+(d!=1 ? "s" : "")+", "+h+"h, "+m+"m";
	}
	
	public static final long getTotalRequests() {
		return lTotalRequests;
	}

	public static final String getFirstRunDate(){
		Date date = new Date(getFirstRunEpoch());
//		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yy");
		SimpleDateFormat df = new SimpleDateFormat("dd MMMM yyyy");
		return df.format(date);//date.toString();//
	}
	
	/*
	 The time a GET request is cached
	 */
	public long getCacheTimeout(){
		if (gets("image").length() > 0 || gets("img").length() > 0)
			return 0;
		
		return 2;
	}
	
	/*
	 For the GET request add this string for the cache elements
	 */
	public String getCacheKeyModifier(){ 
		HttpSession sess = request.getSession(true);
		
		if (sess!=null && sess.getAttribute("pTime")!=null)
			return
			(String) sess.getAttribute("pTime") + "/"+
			(String) sess.getAttribute("sum") + "/" +
			(String) sess.getAttribute("int") + "/" +
			(String) sess.getAttribute("err") + "/" +
			(String) sess.getAttribute("log") + "/" +
			(String) sess.getAttribute("interval.min") + "/"+
			(String) sess.getAttribute("interval.max");
		else
			return "";
	}
	
	/*
	 Initialize the basic variables
	 */
	public final void doInit() {
		ServletContext sc = getServletContext();
		
		sResDir = sc.getRealPath("/");
		if (!sResDir.endsWith("/"))
			sResDir += "/";
		sResDir += "WEB-INF/";
		
		sConfDir = sResDir + "conf/";
		sLogDir =  sResDir + "log/";
		sContentDir = sResDir + "content";
		//sNewsDir = sResDir + "news/";
		
		sResDir += "res/";
		
		readVariables();//after sResDir init, read the variables that must update values in pages

		//set News title
		String sNewsTitle;
		sNewsTitle = (String)hVariables.get("NewsTitle");
		if ( sNewsTitle == null )
		    hVariables.put( "NewsTitle", "<b>Latest News</b>");
		
		//set several variables based on values from hVariables
		sServletName = (String)hVariables.get("ServletName");
		if ( sServletName == null )
			sServletName = this.getClass().getName();//"monsit";// http://se.rogrid.pub.ro/
		
		sLogFile = (String)hVariables.get("LogFile");
		if ( sLogFile==null )
			sLogFile = "mon_site.log";
		sWebResDir = (String)hVariables.get("WebResourcesDirectory");
		if ( sWebResDir == null )
			sWebResDir = "";
		if ( !sWebResDir.endsWith("/") )
			sWebResDir = sWebResDir+"/";
		sWebPath = (String)hVariables.get("WebSitePath");
		if ( sWebPath == null )
			sWebPath = request.getScheme() + "://" + request.getRemoteHost() + "/";
		
		readUsers();//fills user map
		
		if ( (String)hVariables.get("OrderDesc")!=null && ((String)hVariables.get("OrderDesc")).compareTo("yes")==0 )
			bOrderDesc = true;
		else
			bOrderDesc = false;
/*
+" server address: "+request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+"/\r\n"
+" servlet path: "+request.getServletPath()
+ " servlet context name:  "+ getServletContext().getServletContextName()
+ "request host: " + request.getRemoteHost()
 */		
		pMaster = new Page(osOut, sResDir + "masterpage/masterpage"+sResExt);
		
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		
//		int nHeader=1;
//		String sHeader;
//		for ( Enumeration en = request.getHeaderNames(); en.hasMoreElements();) {
//			sHeader = (String)en.nextElement();
//			System.out.println("header "+nHeader+" is "+sHeader+" and its values are: "+request.getHeader(sHeader));
//			nHeader++;
//		}
		//System.out.println("All initialisation done. Using monsit version 1.3.1 built at 2016-11-24 09:06.");
	}
	
	public final String capitalize( String s)
	{
		if ( s==null )
			return "";
		int length = s.length();
		int pos = 0, pos_ant=0;
		char car;
		if( length==0 )
			return "";
		String s_new = "";
		do {
			car = s.charAt(pos);
			if ( car>='a' && car <='z' )
				car += 'A'-'a';
			s_new += car;
			pos_ant = pos;
			pos = s.indexOf(' ', pos_ant);
			if ( pos!=-1 )
				pos ++;
			else
				pos = length;
			s_new += s.substring( pos_ant+1, pos);
		} while ( pos<length );
		return s_new;
	}
	
	public final String getMenuOptionName( String path)
	{
		//if ( path.compareTo(sResDir+"content")==0 )
		if ( path.compareTo("/")==0 ) {
			String sHomeTitle = "MonALISA";
			Object objHT = hVariables.get("HomeTitle");
			if ( objHT!=null && (objHT instanceof String) && ((String)objHT).compareTo("")!=0 )
				sHomeTitle = (String)objHT;
			return sHomeTitle;
		};
		String name;
		name = path.substring(path.lastIndexOf('/')+1);
		if ( name.lastIndexOf(sResExt)>0 )
			name = name.substring( 0, name.lastIndexOf(sResExt));
		//some nice looking transformation from file name to menu option name
		//if ( name.length()>1 )
		//	name = name.substring(0, 1).toUpperCase()+name.substring(1);
		if ( name.compareTo("news") == 0 ) {
			return hVariables.get("NewsTitle").toString();
		}
		if ( name.startsWith("@") )
			name = name.substring(1);
		name = name.replaceAll( "_", " ");
		name = capitalize(name);
		
		return name;//(path.lastIndexOf('.')>0?, path.lastIndexOf('.')):path.substring(path.lastIndexOf('/')+1));
	}
	

	/**
	 * decodes the link received as parameter by using several rules
	 */
	public final String[] decodeLink( String codedParams)
	{
		
		//alloc space for 2 parameters: path and expand
		String[] values = new String[2];
		values[0] = "";
		values[1] = "";
		String sExtension="";
		//get first parameter: tree expand
		if ( codedParams.startsWith("_ne__") ) {
			values[1] = "0";
			codedParams = codedParams.substring(3);
		} else if ( codedParams.startsWith("_e__") ) {
			values[1] = "1";
			codedParams = codedParams.substring(2);
		} else if ( codedParams.startsWith("_logout") ) {
			values[1] = "logout";
			//codedParams = codedParams.substring(7);
			return values;
		} else if ( codedParams.startsWith("_signup") ) {
			values[1] = "signup";
			//codedParams = codedParams.substring(7);
			return values;
		} else if ( codedParams.startsWith("_file") ) {
			int nEndExt = codedParams.indexOf("__", 5);
			values[1] = "file";
			if ( nEndExt!=-1)
				sExtension = "."+codedParams.substring( 5, nEndExt);
			else
				nEndExt = 0;
			codedParams = codedParams.substring(nEndExt);
		} else if ( codedParams.startsWith("_link") ) {
			int nEndExt = codedParams.indexOf(".", 5);
			if ( nEndExt==-1)
				nEndExt = codedParams.length();
			values[1] = "link"+codedParams.substring(5, nEndExt);
			return values;
		} else if ( codedParams.startsWith("_news") ) {
			values[0]="/news";
			values[1] = "";
			return values;
		};
		//transform to path
		//transform from "__" to "/"
		codedParams = codedParams.replaceAll("__", "/");
		//get extension: htm is folder, html is file
		if ( codedParams.endsWith(".htm") )
			values[0] = codedParams.substring(0, codedParams.length()-4)+sExtension;
		else if ( codedParams.endsWith(".html") )
			values[0] = codedParams.substring(0, codedParams.length()-5)+(sExtension.compareTo("")==0?sResExt:sExtension);
		else { //if there is no path, there can be no expand
			values[0] = "";
			values[1] = "";
		};
		return values;
	}
	
	/**
	 * any href to be put in page must go through this page that mangles the link
	 * @param link original link
	 * @return the modified link
	 */
	public final String encodeLink( String link)
	{
		link = link.replaceAll("\\?path=","");
		//first transform "/" to "__"
		link = link.replaceAll("/", "__");
		//link = link.replaceAll("\\?path=/((([^/]+)/)*([^\\.&/]+))(\\.(l))?(&expand=([10]))?",
		//		"_$8__$1.htm$6");
		//second check the expand parameter
		if ( link.endsWith("&expand=1") )
			link = "_e"+link.substring(0, link.length()-9);
		else if ( link.endsWith("&expand=0") )
			link = "_ne"+link.substring(0, link.length()-9);
		if ( link.endsWith(sResExt) )
			link = link.substring(0, link.length()-sResExt.length())+".html";
		else
			link += ".htm";
		return sServletName+link;
	}
	
	//class that encapsulates parameters to be transmited from one instance to the next of the doMenu3 recursive function
	class MenuOpt
	{
		public String menu;
		public boolean bHasItems;
		public MenuOpt()
		{
			bHasItems = false;
			menu = "";
		}
	}
	
	/**
	 * used to keep an entry for a path in menu
	 * so that compared with those from .order, should be 
	 * kept or removed from allList<br>
     * also can contain properties for when rendering this option like:
     * <ul>
     *      <li>name: item's name, used to identify the menu option and that the properties to follow are for it</li>
     *      <li>nice: nice name to be rendered for this option</li>
     *      <li>icon: alternate icon path</li>
     *      <li>icon_open: alternate icon open path (only for directory)</li>
     *      <li>icon_open_sel: alternate icon open selected path (for files is used when option is selected)</li>
     *      <li>title: a link's title</li>
     *      <li>class: special class if desired for a element that will replace the automatically allocated one</li>
     *      <li>class_sel: special class if desired for a selected element that will replace the automatically allocated one</li>
     *      <li>target: attribute for a link that can only be _blank, _parent, _self, _top (only for file)</li>
     *      <li>order: order number used to reposition items in allList vector by iterating through the list, and
     *      inserting the element before the one that has an bigger order number or none at all. First it is removed
     *      from list, because it will get inserted again.</li>
     * </ul><br><br>
	 * type means that the option is a file or a directory, "news" is a file
	 */
	class MenuOptionParams {
		public String name;
        public HashMap properties = new HashMap();
		//public String optionTitle;
		public String type;//news, file or directory
		public MenuOptionParams( String n, String t) {
			name = n;
			type = t;
		}
	}
	
	class MyBool {
		private boolean bValue;
		public MyBool() { bValue=false; }
		public MyBool(boolean bV) { putValue(bV); }
		public boolean getValue() { return bValue;}
		public void putValue( boolean bV) { bValue = bV; }
	}
	
    //list of attributes that can be set for a menu option
    private static final String []sPropsAttributes = new String[] { "name", "nice", "icon", "icon_open", "icon_open_sel", "title", "class", "class_sel", "target", "order"};
	/**
	 * constructs the menu as a dtree menu only as plain html<br>
	 * each directory is another table made of 2 columns:<br>
	 * 		directory icon for first row/ line icon for rest on first column<br>
	 * 		directory name for first row/ subdirectories tables or directory files names for rest on second column<br>
	 * if on current row there is a subdirectory, then the first column will have as background image a vertical line, and
	 * as image in cell, aligned at top, a cross line, only if not the last in tree.<br>
	 * If last, there is only a right angle line image in cell, aligned at top.<br>
	 * If a folder is expanded, all its children are shown (all first level subdirectories and all files).<br>
	 * If a subdirectory has children, then instead of intersection line will have a plus or minus image.<br>
	 * ATTENTION: this function can generate several Security Exceptions that are 
	 * uncaught. Those should be caught in calling function.<br>
	 * @param current_path
	 * @param abs_path
	 * @return Returns constructed menu.
	 */
	public final MenuOpt doMenu3(String current_path, String abs_path, HashMap properties)
	{
        Object obj;
		MenuOpt retMO = new MenuOpt();
		StringBuilder menu= new StringBuilder();
		boolean bHasItems = false;
//		String dir_img;
		File fis;
		MenuOpt auxMO;
        //old
/*		if ( current_path.compareTo("/")==0 ) {
//			String sHomeIcon = "globe.gif";
//			Object objHI = hVariables.get("HomeIcon");
//			if ( objHI!=null && (objHI instanceof String) && ((String)objHI).compareTo("")!=0 )
//				sHomeIcon = (String)objHI;
			dir_img = "<image src=\""+sWebResDir+"img/"+sHomeIcon+"\" align=absmiddle>";
		} else if ( current_path.compareTo(abs_path) == 0 )
			dir_img = "<image src="+sWebResDir+"img/folderopensel.gif align=absmiddle>";
		else if ( request.getSession().getAttribute(current_path)!=null )
		    dir_img = "<image src="+sWebResDir+"img/folderopen.gif align=absmiddle>";
		else dir_img = "<image src="+sWebResDir+"img/folder.gif align=absmiddle>";
		menu.append("\r\n<table border=0 cellspacing=0 cellpadding=0 class=nice>\r\n\t<tr>\r\n\t\t<td>"+dir_img+"</td>\r\n\t\t<td>");
*/        //new 
        //put icon for option
        menu.append("\r\n<table border=0 cellspacing=0 cellpadding=0 class=nice>\r\n\t<tr>\r\n\t\t<td>");
        menu.append("<image src=\""+sWebResDir);
        if ( current_path.compareTo(abs_path) == 0 )
            if ( (obj=properties.get("icon_open_sel"))!=null )
                menu.append(obj.toString());
            else if ( (obj=properties.get("icon_open"))!=null )
                menu.append(obj.toString());
            else if ( (obj=properties.get("icon"))!=null )
                menu.append(obj.toString());
            else
                menu.append( "img/folderopensel.gif");
        else if ( request.getSession().getAttribute(current_path)!=null )
            if ( (obj=properties.get("icon_open"))!=null )
                menu.append(obj.toString());
            else if ( (obj=properties.get("icon"))!=null )
                menu.append(obj.toString());
            else
                menu.append( "img/folderopen.gif");
        else if ( (obj=properties.get("icon"))!=null )
            menu.append(obj.toString());
        else
            menu.append( "img/folder.gif");
        menu.append( "\" align=absmiddle></td>\r\n\t\t<td>");
		//System.out.println("foder path is: "+sContentDir+current_path+(current_path.endsWith("/")?"":"/")+"content.res");
		String aClassCurDir = "";//if current directory is selected, this will be the class used for style
		if ( current_path.compareTo(abs_path) == 0 )
            if ( (obj=properties.get("class_sel"))!=null )
                aClassCurDir = " class=\""+obj.toString()+"\"";
            else
                aClassCurDir = " class=\"nodeSel\"";
        else if ( (obj=properties.get("class"))!=null )
            aClassCurDir = " class=\""+obj.toString()+"\"";
		fis = new File( sContentDir+current_path+(current_path.endsWith("/")?"":"/")+"content"+sResExt);
		if ( fis.isFile() ) {
			if ( current_path.compareTo("/")==0 ) {
//				String sHomeAlt = "MONitoring Agents using a Large Integrated Services Arhitecture";
//				Object objHA = hVariables.get("HomeAlt");
//				if ( objHA!=null && (objHA instanceof String) && ((String)objHA).compareTo("")!=0 )
//					sHomeAlt = (String)objHA;
				menu.append("<a href=\""+encodeLink("")+"\"");
			} else
				menu.append( "<a href=\""+encodeLink("?path="+current_path)+"\"");
            //append title
            if ( (obj=properties.get("title"))!=null )
                menu.append( " title=\""+obj.toString()+"\"");
            //append class
            menu.append( aClassCurDir+">");
		};
        //menu.append( getMenuOptionName(current_path));
        menu.append( ((obj=properties.get("nice"))!=null?obj.toString():getMenuOptionName(current_path)));
		if ( fis.isFile() )
			menu.append( "</a>");
		menu.append( "</td>\r\n\t<tr>");
		fis = new File( sContentDir+current_path);
		File[] fileList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				String sFileName = pathname.getName();
				if ( pathname.isFile() && sFileName.compareTo("content"+sResExt)!=0 
						&& sFileName.endsWith(sResExt) && !sFileName.startsWith(".") 
						//&& !sFileName.startsWith("@") 
						)
					return true;// "." is for hidden files and "@" is for special interpretted files
				return false;
			}
		});
		orderFileList( fileList, bOrderDesc);
		final MyBool myHasNews = new MyBool();
		boolean bHasNews = false; //check while getting dirs, if news is there
		File[] dirList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				if ( pathname.getName().equals("news") ) //if news directory in current path
					myHasNews.putValue(true); //remember it
				//select only directories that don't start with ".", those are hidden
				if ( pathname.isDirectory() && !pathname.getName().startsWith(".") 
					&& !pathname.getName().equals("news") )
					return true;
				return false;
			}
		});
		orderFileList( dirList, bOrderDesc);
		if ( fileList!=null && fileList.length>0 || dirList!=null && dirList.length>0 )
			bHasItems = true;//has at least one children
		if ( myHasNews.getValue() )
			bHasNews = true;
		//no more needed as the check for news dir is done while enumerating dirs in current dir
/*		if ( current_path.compareTo(sPathToNews)==0 ) {
			File fNewsDir = new File(sContentDir+current_path+(current_path.endsWith("/")?"":"/")+"news");
			if ( fNewsDir.exists() && fNewsDir.isDirectory() )
				bHasNews = true;
		};*/
		//if this directory has items and is root or is opened, render its content
		if ( ( bHasItems || bHasNews ) 
			&& ( current_path.compareTo("/")==0 || request.getSession().getAttribute(current_path)!=null ) ) {
			//construct all options list
			Vector allList = new Vector();
			//current_path with slash
			String current_pathWS = current_path+(current_path.endsWith("/")?"":"/");
			//check news option
			if ( bHasNews )
				allList.add(new MenuOptionParams( /*current_pathWS+*/"news", "file"));
			//if current path is not base path and current path must be expanded, expand it
			if ( current_path.compareTo("/")==0 || request.getSession().getAttribute(current_path)!=null ) {
				//add all files
				if ( fileList!= null )
					for ( int i=0; i<fileList.length; i++ )
						allList.add( new MenuOptionParams( /*current_pathWS+*/fileList[i].getName(), "file"));
				//add all directories
				if ( dirList!= null )
					for ( int i=0; i<dirList.length; i++ )
						allList.add( new MenuOptionParams( /*current_pathWS+*/dirList[i].getName(), "directory"));
			}
			readListProperties(allList, sContentDir+current_pathWS);
    		allList = orderList(allList, sContentDir+current_pathWS);
			//now that the list has been validated against .order file, render it
            MenuOptionParams mop;
			String filepath;
			int len = allList.size();
			for( int i=0; i<len; i++) {
				mop = (MenuOptionParams)allList.get(i);
				filepath = current_pathWS+mop.name;
				if ( mop.type.equals("file") ) {
					//construct as link
					//set an continuing down vertical bar
					menu.append( "\r\n\t<tr>\r\n\t\t<td");
					//if not last item in directory, set as background a vertical line to strech vertically for as long as neccessary
					if ( i<len-1 )
						menu.append(" class=\"vert_line\"");
					menu.append( ">");
					menu.append( "<img src=\""+sWebResDir+"img/joinbottom.gif\"></td>\r\n\t\t<td><table border=0 cellspacing=0 cellpadding=0 class=nice><tr><td>");
					if ( mop.name.startsWith("@") ) {//link file
					    String []options = { "link", "icon", "title"};
					    readPropertiesFromFile( options, sContentDir+filepath);
					    if ( options[1].compareTo("")!=0 )
					        menu.append( "<img src=\""+sWebResDir+options[1]+"\" align=absmiddle></td><td><a href=\"");
					    else
					        menu.append( "<img src="+sWebResDir+"img/page.gif align=absmiddle></td><td><a href=\"");
						menu.append( options[0]//readPropertyFromFile( "link", sContentDir+filepath)
							+ "\" target=\"_blank\"");	//set the link of option, if special file ("@") the put link provided inside of file
					    if ( options[2].compareTo("")!=0 )
					        menu.append( " title=\"" + options[2] + "\"");
					} else {
					    //set icon for news...
                        //old stuff
//					    menu += "<img src="+sWebResDir+"img/page"+(mop.name.equals("news")?"_news":"")+".gif align=absmiddle></td><td><a href=\"";
//						menu += encodeLink("?path="+filepath) + "\"";
                        //new stuff
                        //append file icon
                        menu.append( "<img src=\""+sWebResDir);
                        if ( mop.name.equals("news") )
                            menu.append( "img/page_news.gif");
                        else if ( abs_path.compareTo(filepath) == 0 && (obj=mop.properties.get("icon_open_sel"))!=null )
                            menu.append( obj.toString());
                        else if ( (obj=mop.properties.get("icon"))!=null )
                            menu.append( obj.toString());
                        else
                            menu.append( "img/page.gif");
                        menu.append( "\" align=absmiddle></td><td><a href=\"");
                        //append link url
                        menu.append( encodeLink("?path="+filepath) + "\""); 
                        //append class
                        menu.append( ( abs_path.compareTo(filepath) == 0? ((obj=mop.properties.get("class_sel"))!=null?" class=\""+obj.toString()+"\"":" class=\"nodeSel\"") : ((obj=mop.properties.get("class"))!=null?" class=\""+obj.toString()+"\"":"") ) );
                        //append title
                        menu.append( ((obj=mop.properties.get("title"))!=null?" title=\""+obj.toString()+"\"":"") );
                        //append target
                        menu.append( ((obj=mop.properties.get("target"))!=null?" target=\""+obj.toString()+"\"":"") );
					}
                    //append nice name
                    menu.append( ">"+((obj=mop.properties.get("nice"))!=null?obj.toString():getMenuOptionName(filepath))+"</a>" + "</td></tr></table></td>\r\n\t</tr>");
				} else if ( mop.type.equals("directory") ) {
					//set an continuing down vertical bar
					menu.append( "\r\n\t<tr>\r\n\t\t<td valign=top");
					if ( i<len-1 )
						menu.append(" class=\"vert_line\"");
					menu.append( ">");
					auxMO = doMenu3( filepath, abs_path, mop.properties);
					if ( auxMO.bHasItems ) {//has items, so a plus or minus sign has to be set
						//check to see if is in session, meaning that it si expanded
						if ( request.getSession().getAttribute( filepath)!=null )
							menu.append( "<a href=\""+encodeLink("?path="+filepath+"&expand=0")+"\"><img src=\""+sWebResDir+"img/minusbottom.gif\"></a>");
						else
							menu.append( "<a href=\""+encodeLink("?path="+filepath+"&expand=1")+"\"><img src=\""+sWebResDir+"img/plusbottom.gif\"></a>");
					} else
						menu.append( "<img src=\""+sWebResDir+"img/joinbottom.gif\">");
					menu.append("</td>\r\n\t\t<td>"+auxMO.menu+"\r\n\t\t</td>\r\n\t</tr>");
				};
			}
		}
		//put the news link in menu, right below root: MonALISA
/*		if ( current_path.compareTo("/")==0 ) {
			File fNewsDir = new File(sNewsDir);
			if ( fNewsDir.exists() && fNewsDir.isDirectory() ) {
				menu += "\r\n\t<tr>\r\n\t\t<td";
				//if not last item in directory, set as background a vertical line to strech vertically for as long as neccessary
				if ( bHasItems )
					menu+=" class=\"vert_line\"";
				menu += ">";
				menu+= "<img src=\""+sWebResDir+"img/joinbottom.gif\"></td>\r\n\t\t<td><table border=0 cellspacing=0 cellpadding=0 class=nice>" +
						"<tr><td><img src="+sWebResDir+"img/page_news.gif align=absmiddle></td><td>"+
						"<a href=\""+encodeLink("?path=/news")+"\""
						+ ( abs_path.compareTo("/news") == 0? " class=\"nodeSel\"" : "" )
						+"><b>"+hVariables.get("NewsTitle")+"</b></a>" + "</td></tr></table></td>\r\n\t</tr>";// sServletName+"_news.html"
			};
		};
*/		//end put news link
		//if current path is not base path and current path must be expanded, expand it
/*		if ( current_path.compareTo("/")==0 || request.getSession().getAttribute(current_path)!=null ) {
			String filepath;
			String optionname;
			//put files in menu
			if ( fileList!= null )
				for ( int i=0; i<fileList.length; i++ ) {
					filepath = current_path+(current_path.endsWith("/")?"":"/")+fileList[i].getName();
					//if ( filepath.compareTo(abs_path) == 0 )
						//index_selected = next_index;
					optionname = getMenuOptionName(filepath);
					//construct as link
					//set an continuing down vertical bar
					menu += "\r\n\t<tr>\r\n\t\t<td";
					//if not last item in directory, set as background a vertical line to strech vertically for as long as neccessary
					if ( i!=fileList.length-1 || dirList.length!=0 )
						menu+=" class=\"vert_line\"";
					menu += ">";
					menu+= "<img src=\""+sWebResDir+"img/joinbottom.gif\"></td>\r\n\t\t<td><table border=0 cellspacing=0 cellpadding=0 class=nice>" +
							"<tr><td>";
					if ( fileList[i].getName().startsWith("@") ) {//link file
					    String []options = { "link", "icon", "title"};
					    readPropertiesFromFile( options, sContentDir+filepath);
					    if ( options[1].compareTo("")!=0 )
					        menu += "<img src=\""+sWebResDir+options[1]+"\" align=absmiddle></td><td><a href=\"";
					    else
					        menu += "<img src="+sWebResDir+"img/page.gif align=absmiddle></td><td><a href=\"";
						menu += options[0]//readPropertyFromFile( "link", sContentDir+filepath)
							+ "\" target=\"_blank\"";	//set the link of option, if special file ("@") the put link provided inside of file
					    if ( options[2].compareTo("")!=0 )
					        menu += " title=\"" + options[2] + "\"";
					} else {
					    menu += "<img src="+sWebResDir+"img/page.gif align=absmiddle></td><td><a href=\"";
						menu += encodeLink("?path="+filepath) + "\""; 
					}
					menu += ( filepath.compareTo(abs_path) == 0? " class=\"nodeSel\"" : "" )
							+">"+optionname+"</a>" + "</td></tr></table></td>\r\n\t</tr>";
				}
			String dirpath;
			//put directories in menu, recursively
			if ( dirList!= null )
				for ( int i=0; i<dirList.length; i++ ) {
					dirpath = current_path+(current_path.endsWith("/")?"":"/")+dirList[i].getName();
					//set an continuing down vertical bar
					menu += "\r\n\t<tr>\r\n\t\t<td valign=top";
					if ( i!=dirList.length-1 )
						menu+=" class=\"vert_line\"";
					menu += ">";
					auxMO = doMenu3( dirpath, abs_path);
					if ( auxMO.bHasItems ) {//has items, so a plus or minus sign has to be set
						//check to see if is in session, meaning that it si expanded
						if ( request.getSession().getAttribute( dirpath)!=null )
							menu += "<a href=\""+encodeLink("?path="+dirpath+"&expand=0")+"\"><img src=\""+sWebResDir+"img/minusbottom.gif\"></a>";
						else
							menu += "<a href=\""+encodeLink("?path="+dirpath+"&expand=1")+"\"><img src=\""+sWebResDir+"img/plusbottom.gif\"></a>";
					} else
						menu+= "<img src=\""+sWebResDir+"img/joinbottom.gif\">";
					menu+="</td>\r\n\t\t<td>"+auxMO.menu+"\r\n\t\t</td>\r\n\t</tr>";
				}
		}
*/
		menu.append( "\r\n</table>");
		retMO.menu = menu.toString();
		retMO.bHasItems = bHasItems;
		return retMO;
	}
	
	public final void execGet() {
		Page pAuthU=null, pAuthE=null, pError=null, pSignUp=null, pInfo=null, pAuthTitle=null, pAuthOr=null;
		File fpath;
		String pr = gets( "pr");
		long lStartTime=NTPDate.currentTimeMillis();
		//System.out.println("starting executing page: "+pr);
		 /**
			//if no encoding set up, then uncomment this code to transform 
			//from normal get to neccessary encoding 
			String pa = gets( "path");
			if ( !pa.startsWith("/") )
				pa = "/"+pa;
			pa.replaceAll("/","__");
			if ( pa.endsWith(sResExt) )
				pa = pa.substring(0,pa.length()-sResExt.length())+".html";
			else
				pa += ".htm";
			String e = gets( "expand");
			if ( e.compareTo("1")==0 )
				e = "_1";
			else if ( e.compareTo("0")==0 )
				e = "_0";
			else e = "";
			pr = e+pa;
			//end mangling
		*/
		//decode received parameter
		String [] params = decodeLink( pr);
		//for( int i=0; i<params.length; i++)
			//System.out.println(" param["+i+"]=\""+params[i]+"\"");
		
		//if user wants to logout
		if ( params[1].compareTo("logout")==0 ) {
			request.getSession().removeAttribute("userID");
			request.getSession().removeAttribute("email");
		};
		
		String path = params[0];//gets( "path");
		if ( path==null )
			path = "";
		if ( !path.startsWith("/") )
			path = "/"+path;
		//if any try for back path, remove it: /../
		path.replaceAll("/../","/");

		//set to have something in session as lastContent
		if ( request.getSession().getAttribute("lastContent") == null 
			|| ((String)request.getSession().getAttribute("lastContent")).compareTo("") == 0)
			request.getSession().setAttribute("lastContent", "/");

		/**
		 * check request for input forms to obtain user id
		 */
		String sErrorMessage = "";//any error message gets loaded into error.l
		String sInfoMessage = "";//any info message gets loaded into info.l
		//check to see if user tried to authenticate
		if ( gets("authuser").compareTo("")!=0 ) {
			Integer UID;
			String[] uData;
			boolean bFound = false;
			for ( Iterator it = hUsers.keySet().iterator(); it.hasNext();) {
				UID = (Integer)it.next();
				//System.out.println("UID="+UID);
				uData = (String [] )hUsers.get( UID);
				//System.out.println("uData[0]="+uData[0]+" uData[1]="+uData[1]);
				if ( uData[0].compareTo(gets("authuser"))==0 
						&& uData[1].compareTo(gets("authpasswd"))==0 ) {
					request.getSession().setAttribute("userID", UID.toString());
					bFound = true;
				};
			}
			if ( !bFound )
				sErrorMessage += "Invalid user or password.<br>";
				//sLoginError = "Error. Invalid user or password.";
		} else if ( gets("signuser").compareTo("")!=0 ) {
			//check if new user already registered
			Integer UID;
			int uidMax=0;
			String[] uData;
			boolean bIsRegistered = false;
			for ( Iterator it = hUsers.keySet().iterator(); it.hasNext();) {
				UID = (Integer)it.next();
				if ( UID.intValue() > uidMax )
					uidMax = UID.intValue();
				//System.out.println("UID="+UID);
				uData = (String [] )hUsers.get( UID);
				//System.out.println("uData[0]="+uData[0]+" uData[1]="+uData[1]);
				if ( uData[0].compareTo(gets("signuser"))==0 ) {
					sErrorMessage += "User already registered.<br>";
					//sSignUpError = "Error. User already registered.";
					bIsRegistered = true;
				};
			}
			if ( !bIsRegistered ) {
				//register the new user with uidMax+1
				uidMax++;
				//it CAN happen that the new generated id would not be unique
				//FIRST the fields MUST be verified
				try {
					BufferedWriter bw =new BufferedWriter(new FileWriter(sConfDir+"users"+sConfExt, true));
					bw.newLine();
					//as an optimisation, uData can be not allocated
					uData = new String[4];
					uData[0] = gets("signuser");
					uData[1] = gets("signpasswd");
					uData[2] = gets("signemail");
					uData[3] = gets("signname");
					hUsers.put( Integer.valueOf(uidMax), uData);
					bw.write( uidMax+" "+gets("signuser")+" "+gets("signpasswd")+" "
						+gets("signemail")+" "+gets("signname"));
					bw.flush();
					bw.close();
					request.getSession().setAttribute("userID", ""+uidMax);
					//System.out.println("new user registered: "+uidMax);
				} catch ( IOException ioex ) {
					//this is a warning
					sErrorMessage += "Could not save user data.<br>";
					//sSignUpError = "Error. Could not save user data.";
				}
			};
		} else if ( gets("onlyemail").compareTo("")!=0 ) {
			//user authenticates himself with email
			//System.out.println("email="+gets("onlyemail"));
			//check the email in the valid adresses file
			if ( !checkValidEmail( gets("onlyemail")) ) {
				//this email is not valid or not confirmed yet
				//so send email and anounce user that the email was sent
				//this add a new line in confirmations.l
				if ( !newConfirmation( gets("onlyemail"), path, (String)request.getSession().getAttribute("lastContent")) ) {
					//in case of error, say something
					sErrorMessage += "Could not send mail to the provided email address.<br>";
				} else
					sInfoMessage += "An email has been sent to your email address containing a link that will give" +
							" you the possibility to download the file.";
				
				//when the link will be clicked, the email will become valid
				
				//check for this email address in confirmations file?
				//to anounce that an email was sent to this address for confirmation?
				/*
				if ( checkConfirmEmail( gets("onlyemail")) {
					//this email is valid, it must be put in valid emails file
					BufferedWriter bw =new BufferedWriter(new FileWriter(sResDir+"valid_emails.l", true));
					bw.newLine();
					bw.write( gets("onlyemail"));
					bw.flush();
					bw.close();
					//email found, so put it in session
					request.getSession().setAttribute("email", gets("onlyemail"));
				}
				*/
			} else //valid email, so put it in session
				request.getSession().setAttribute("email", gets("onlyemail"));
			//System.out.println("error message: "+sErrorMessage);
			//System.out.println("info message: "+sInfoMessage);
		}
		//the link provided into a mail is also a kind of an input
		if ( params[1].startsWith("link") ) {
			//if user clicked on a link to get something, then show him the  lastContent page,
			//to which it is supposed to have access, since it had access no long time ago
			//and then redirect him to the nolink page that is the one that provides the download
			//check the provided code
			//first retrive code, that must be like:
			String code = params[1].substring(4);
			if ( code.lastIndexOf('.')!=-1 )
				code = code.substring( 0, code.lastIndexOf('.'));
			//puts in session the email, and the last content and returns the new path
			String alt_path = checkConfirmation( code);
			if ( alt_path ==null ) {//no link confirmation, so give an error message
				sErrorMessage = "Invalid code "+code;
			} else { //set the path variable as the current path
				//set params[1] to correct value so that the download can start
				params[1] = "file";
				path = alt_path;
				//all other required variables are already set in checkCofirmation function
			}
		}
		
		/* do a nice path */
		String sNicePath = path;
		if ( sNicePath.startsWith("/") )
			sNicePath = sNicePath.substring(1);
		sNicePath = sNicePath.replaceAll( "/", " > ");
		sNicePath = sNicePath.replaceAll("_", " ");
		sNicePath = capitalize( sNicePath);
		if ( sNicePath.endsWith(sResExt) )
			sNicePath = sNicePath.substring(0, sNicePath.length()-sResExt.length());
		/**
		 * get userID from Session, lookup name in hash and write logout link
		 */
		int usrID;
		try {
			usrID = Integer.parseInt( (String)request.getSession().getAttribute("userID"));
			/*Integer UID = */Integer.valueOf(usrID);
		} catch (NumberFormatException nfex) {
			usrID = -1;
		}
		
		/**
		 * get available email from Session
		 */
		String sEmail = (String)request.getSession().getAttribute("email");
		if ( sEmail == null ) sEmail = "";
		
		/************************* check access to file *****************************/
		int rez = checkUserAccessForFile( usrID, path);
		if ( rez==1 || rez==0 || rez==2 )//log granted file access
			writeLineInLog( path, ""+usrID);
		System.out.println(showDate(new Date())+" request="+path+" user="+usrID+" email="+sEmail+" UserAccessForFile= "+rez);
		/*********************** end check access to file ***************************/
		
		boolean bUseLastPage = false;
		if ( rez<-1) {//page requires authentication and user invalid or/and email invalid
			if ( rez > -4 ) {//access possible with user login
				//user invalid
				if ( usrID==-1 ) {//no user logged in yet
					pAuthU = new Page(sResDir+"auth"+sResExt);
					setVariablesOnPage( pAuthU);
					setSignUpLogOutLink( pAuthU, usrID);
					pAuthU.modify( "page_path", sNicePath);//encodeLink("?path="+path));
					pAuthU.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
					//pAuth.modify( "login_error", sLoginError);
					bUseLastPage = true;
				} else {//invalid user
					//show error message for access denied on the page
					sErrorMessage += "Access denied for current logged user on page.<br>";
					bUseLastPage = true;
				}
			}
			if ( rez <= -3 ) {//email invalid
				//authentication based on email required
				if ( sEmail.compareTo("")==0 ) {
					pAuthE = new Page(sResDir+"email"+sResExt);
					setVariablesOnPage( pAuthE);
					setSignUpLogOutLink( pAuthE, usrID);
					pAuthE.modify( "page_path", sNicePath);
					pAuthE.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
					//pAuth.modify( "login_error", sLoginError);
					bUseLastPage = true;
				}
			}
			//check to see if authentication required, and if so, check to see which pages are to be created, AuthTitle and/or AuthOr
			if ( pAuthU!=null || pAuthE!=null ) {
				pAuthTitle = new Page(sResDir+"login_title"+sResExt);
				setVariablesOnPage( pAuthTitle);
				setSignUpLogOutLink( pAuthTitle, usrID);
				pAuthTitle.modify( "page_path", sNicePath);//encodeLink("?path="+path));
				pAuthTitle.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
			}
			if ( pAuthU!=null && pAuthE!=null ) {
				pAuthOr = new Page(sResDir+"login_or"+sResExt);
				setVariablesOnPage( pAuthOr);
				setSignUpLogOutLink( pAuthOr, usrID);
			}
		//} else if ( rez == -1 ) {
			//if path not found it means is not in files.conf
			//sErrorMessage = "Path not found.";
			//path = (String)request.getSession().getAttribute("lastContent");
			//if ( path==null || path.compareTo("")==0 )
				//path = "/";
		} else if ( params[1].compareTo("file")==0 ) { //if user acccessed a file to view its content, return it:
/*			if ( path.endsWith(sResExt) )
				path = path.substring(0, path.length()-sResExt.length());
			String sFileExt;
			sFileExt = params[1].substring(4);
			if ( sFileExt.compareTo("")!=0 )
				sFileExt = "." + sFileExt;
			path += sFileExt;
*/			//test if file requested exists
/*
 //a possible code
	//1.  Get the extension and create the filename
	String ext = methodToReturnExtension();
	String fileName = "ConopsDocument" + ext;
	//2.  Set the headers into the request.  "application/octet-stream means that this
	//    is a binary file.
	response.setContentType("application/octet-stream");
	response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
	//3.  Write the file to the output stream.
	BufferedInputStream bufferedInputStream = new BufferedInputStream( methodToReturnInputStream() );
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	int data = -1;
	while ( (data = bufferedInputStream.read( )) != -1 ){	
		byteArrayOutputStream.write( data);
	}
	bufferedInputStream.close();
	response.setContentLength( byteArrayOutputStream.size() );
	response.getOutputStream().write( byteArrayOutputStream.toByteArray() );
	response.getOutputStream().flush();
	response.setHeader("Cache-Control", "no-cache");
 */
 			try {
				fpath = new File(sContentDir + path);
				if ( fpath.isFile() ) {
					String sAbsWebPath = sWebResDir;
					if ( sWebResDir.endsWith("/") )
						sAbsWebPath = sWebResDir.substring(0, sWebResDir.length()-1);
					response.setHeader( "Location", sAbsWebPath+path);
					osOut.write(("web address: "+sAbsWebPath+path).getBytes());
					/*
					String sFileExt = path.substring(path.lastIndexOf('.'));
					if ( sFileExt.compareTo(".jnlp")==0 )
						response.setContentType("application/x-java-jnlp-file");
					else
					    response.setContentType("application/octet-stream");
					response.addHeader( "content-disposition", "attachment; filename=\""+path.substring(path.lastIndexOf('/')+1)+"\";");
					response.addHeader( "content-length", ""+fpath.length());
					try {
						FileInputStream fis = null;
						try {
							fis = new FileInputStream(sContentDir + path);
						} catch (FileNotFoundException fnfex) {
							osOut.write("File not found.".getBytes());
						};
						if ( fis!=null ) {
							byte []buff = new byte[1024];
							int buf_len;
							while ( (buf_len=fis.read(buff))!=-1 ) {
								osOut.write( buff, 0, buf_len);
							}
						};
						osOut.flush();
						fis.close();
						//osOut.close();
					} catch ( IOException ioex ) {
						//could not write to output stream
						System.out.println("IOException in execGet for read content of file");
						ioex.printStackTrace();
					}
	//				PipedOutputStream pos = new PipedOutputStream();
	//				pos.
	//				PipedInputStream pis = new PipedInputStream()
	//				osOut.write( fis.rea)
					//pMaster = new Page(osOut, sContentDir + path);
					//pMaster.write();
					 */
					bAuthOK = true;
					return;
				} else {//no file
					//show error message for no available file
					sErrorMessage += "Requested file not available.<br>";
					bUseLastPage = true;
				}
				
			} catch (Exception ex ) {
				System.out.println("Download file exception:");
				ex.printStackTrace();
				//show error message for no available file
				sErrorMessage += "Requested file not available.<br>";
				bUseLastPage = true;
			}
		};
		
		//if user wants to signup
		if ( params[1].compareTo("signup")==0 ) {
			pSignUp = new Page(sResDir+"signup"+sResExt);
			setVariablesOnPage( pSignUp);
			setSignUpLogOutLink( pSignUp, usrID);
			pSignUp.modify( "page_path", sNicePath);
			pSignUp.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
			//pSignUp.modify( "signup_error", sSignUpError);
			bUseLastPage = true;
		};
		
		
		

		//set response content type
		response.setContentType("text/html");
		
		//if there was any info, show it/them
		if( sInfoMessage.compareTo("")!=0 ) {
			pInfo = new Page(sResDir+"info"+sResExt);
			setVariablesOnPage( pInfo);
			setSignUpLogOutLink( pInfo, usrID);
			pInfo.modify( "page_path", sNicePath);
			pInfo.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
			pInfo.modify( "info_message", sInfoMessage);
		}
		//if there was any error, show it/them
		if( sErrorMessage.compareTo("")!=0 ) {
			pError = new Page(sResDir+"error"+sResExt);
			setVariablesOnPage( pError);
			setSignUpLogOutLink( pError, usrID);
			pError.modify( "page_path", sNicePath);
			pError.modify( "page_name", sServletName+pr);//encodeLink("?path="+path));
			pError.modify( "error_message", sErrorMessage);
		}

		//set global variables on master page, if any...
		setVariablesOnPage( pMaster);
		//set signup or logout link, if the case
		setSignUpLogOutLink( pMaster, usrID);
		/**
		 * append pages to master page
		 */
		if ( pInfo!=null )
			pMaster.append(pInfo);
		if ( pError!=null )
			pMaster.append(pError);
		if ( pAuthTitle!=null ) //title page will only be available if pAuthU or pAuthE are not null
			pMaster.append(pAuthTitle);
		if ( pAuthU!=null )
			pMaster.append(pAuthU);
		if ( pAuthOr!=null ) //or page will only be available if pAuthU and pAuthE are not null
			pMaster.append(pAuthOr);
		if ( pAuthE!=null )
			pMaster.append(pAuthE);
		if ( pSignUp!=null )
			pMaster.append(pSignUp);
		
		/**
		 * here is a bifurcation: normal content of page
		 * and news content which is generated based on news directory
		 */
		if ( path.endsWith( "/news") ) { //check only if it ends with "/news"
			//if this is the news section, do something else:
			//if there is content for news page, show it first, and, in it, insert all news
			//so, this would be a bigger template
			Page pNewsContent = new Page( sContentDir + path + "/content" + sResExt);
			setSignUpLogOutLink( pNewsContent, usrID);
			setVariablesOnPage( pNewsContent);
			//first load the template page for news
			Page pNewsTemplate = new Page( sContentDir + path + "/news" + sResExt);
			//set global variables on this page, if any...
			setSignUpLogOutLink( pNewsTemplate, usrID);
			setVariablesOnPage( pNewsTemplate);
			//then, for each file in news directory, load in template page the available news content page
			File fNewsDir = new File( sContentDir + path);
			if ( fNewsDir.isDirectory() ) {//if there is a directory with news...
				File[] fileList = fNewsDir.listFiles( new FileFilter() {
					public boolean accept(File pathname) {
						String sFileName = pathname.getName();
						if ( pathname.isFile() && sFileName.compareTo("content"+sResExt)!=0 
								&& sFileName.compareTo("news"+sResExt)!=0
								&& sFileName.endsWith(sResExt) )
							return true;
						return false;
					}
				});
				orderFileList( fileList, false);//bOrderDesc);
				//transform from file list to menuoptionparams list
				Vector unsortedList = new Vector();
				if ( fileList!=null )
					for ( int i=0; i<fileList.length; i++)
						unsortedList.add(new MenuOptionParams(fileList[i].getName(),"a_news"));
				Vector sortedList = orderList(unsortedList, sContentDir + path + "/");
				for( int i=0; i<sortedList.size(); i++)  {
					Page pOneNews = new Page( sContentDir + path + "/" + ((MenuOptionParams)sortedList.get(i)).name );
					setVariablesOnPage( pOneNews);
					setSignUpLogOutLink( pOneNews, usrID);
					pNewsTemplate.append(pOneNews);
					//and last, but not least, append it to content
					//with content for each news
					pNewsContent.append(pNewsTemplate);
				}
			}
			pMaster.append(pNewsContent);
			request.getSession().setAttribute("lastContent", sContentDir + path);
		} else {
			//System.out.println("prepare for creation of content");
			//save last path with content in session so that if current path hasn't any content, get last one
			//gets the content for right part
			String content = "";
			boolean bHasContent = false;
			if ( !bUseLastPage ) {
				//first, tries current path
				fpath = new File(sContentDir + path);
				if ( fpath.isFile() ) {
					content = path;
					request.getSession().setAttribute("lastContent", content);
					bHasContent = true;
				} else if ( fpath.isDirectory() ) {
					content = path + (path.endsWith("/")?"":"/")+"content"+sResExt;
					fpath = new File(sContentDir + content);
					if ( fpath.isFile() ) {
						request.getSession().setAttribute("lastContent", path);
						bHasContent = true;
					};
				};
			};
			if ( !bHasContent ) {//content not found for current path so set previous content
				String last_path = (String)request.getSession().getAttribute("lastContent");
				fpath = new File(sContentDir + last_path);
				 if ( fpath.isFile() ) {
					content = last_path;
					bHasContent = true;
				 } else if ( fpath.isDirectory() ) {
					content = last_path + (last_path.endsWith("/")?"":"/")+"content"+sResExt;
					fpath = new File(sContentDir + content);
					if ( fpath.isFile() )
						bHasContent = true;
				 };
			};
			if ( !bHasContent ) //if the current and last content are invalid, set the main content
				content = sContentDir+"/content"+sResExt;
	
			if ( pAuthU==null && pAuthE==null && pSignUp==null ) {//if authentication needed, do not show last page at all
			    //or signup
				Page p = new Page(sContentDir + content);
				//set global variables on this page, if any...
				setVariablesOnPage( p);
				setSignUpLogOutLink( p, usrID);
				//System.out.println("content page: "+sContentDir+content);
				pMaster.append(p);
			};
		};
/*		Enumeration eParams = request.getParameterNames();
		while (eParams.hasMoreElements()){
			String sParameter = (String) eParams.nextElement();
			//if (!sParameter.equals("monsit"))
			//prop.setProperty(sParameter, gets(sParameter));
		}
*/		
		/**
		 * menu construction
		 */
		//expand all parent nodes to last (file or directory)
		//recursive, from closest parent to fartest
		String parentPath = path;
		int nSlash = 0;
		do {
			nSlash = parentPath.lastIndexOf('/');
			if ( nSlash!=-1 && nSlash!=0 ) {
				parentPath = parentPath.substring(0, nSlash);
				//System.out.println("parent path to set expanded: "+parentPath);
				request.getSession().setAttribute( parentPath, "1");
			};
		} while ( nSlash>0 );
		
		//check expand mode with session
		String expand = params[1];//gets( "expand");
		if ( expand.compareTo("1")==0 )
			request.getSession().setAttribute( path, "1");
		else if ( expand.compareTo("0")==0 )
			request.getSession().removeAttribute( path);
		else
			request.getSession().setAttribute( path, "1");

		//constructs the menu on left side of the page
		try {
            //construct the properties for root option
            HashMap hRoot = new HashMap();
            //get icon
            String sHomeIcon = "img/globe.gif";
            Object objHI = hVariables.get("HomeIcon");
            if ( objHI!=null && (objHI instanceof String) && ((String)objHI).compareTo("")!=0 )
                sHomeIcon = (String)objHI;
            hRoot.put("icon", sHomeIcon);
            String sHomeAlt = "MONitoring Agents using a Large Integrated Services Arhitecture";
            Object objHA = hVariables.get("HomeAlt");
            if ( objHA!=null && (objHA instanceof String) && ((String)objHA).compareTo("")!=0 )
                sHomeAlt = (String)objHA;
            hRoot.put("title", sHomeAlt);
            String sHomeTitle = "MonALISA";
            Object objHT = hVariables.get("HomeTitle");
            if ( objHT!=null && (objHT instanceof String) && ((String)objHT).compareTo("")!=0 )
                sHomeTitle = (String)objHT;
            hRoot.put("nice", sHomeTitle);
			MenuOpt mobj = doMenu3("/",path, hRoot);//path starts with /
			pMaster.modify( "menu", mobj.menu);
		} catch( Exception ex ) {
			ex.printStackTrace();
		}
		
		//initMasterPage(pMaster, prop, sResDir);
		
		try {
			//byte []test="this is a test".getBytes();
			//osOut.write( test);
			//System.out.println("before write");
			pMaster.write();
			//System.out.println("after write");
		} catch ( Exception ex) {
		    System.out.println("Exception in monsit:execGet at output content of page!");
			ex.printStackTrace();
		}
		long lEndTime=NTPDate.currentTimeMillis();
		System.out.println("Execution in "+(lEndTime-lStartTime)+"ms of page: "+pr);
		
		bAuthOK = true;
	}
	
	/**
	 * check to see if the code exists in the confirmations file<br>
	 * and if so, fills session with email and lastContent, and returns path to file<br>
	 * if CacheEmails is active, the email is stored in the valid_email file
	 * @param code
	 * @return the path to file
	 */
	public final String checkConfirmation(String code) {
		// 
		try {
			String ret_path = null;
			String sLine;
			int nSpace;
			synchronized ( objCaccesSync ) {
				int nLine=0;
				BufferedReader br =new BufferedReader(new FileReader(sConfDir+"confirmations"+sConfExt));
				do {
					//read line
					sLine = br.readLine();nLine++;
					//check to see if not comment or EOF
					if ( sLine!=null && !sLine.startsWith("#") ) {
						sLine = sLine.trim();
						//parse the line to get code, email, path and lastContent
						//TODO: there are several constraints for code, path and lastContent, and also for email
						nSpace = sLine.indexOf(' ');
						if ( nSpace>0 ) {//it CAN'T be 0
							if ( code.compareTo(sLine.substring(0, nSpace))==0 ) {
								//code found so get email, path and lastContent
								sLine = sLine.substring(nSpace).trim();
								nSpace = sLine.indexOf(' ');
								if ( nSpace < 0 )
									return null;//error getting email
								String email;
								email = sLine.substring(0, nSpace);
								//System.out.println("email: "+email);
								request.getSession().setAttribute("email", email);
								sLine = sLine.substring(nSpace).trim();
								nSpace = sLine.indexOf(' ');
								if ( nSpace < 0 )
									nSpace = sLine.length();//return null;//error getting path to file to download
								ret_path = sLine.substring(0, nSpace);
								sLine = sLine.substring(nSpace).trim();
								if ( sLine.compareTo("")==0 )
									sLine = "/";
								//System.out.println("last content: "+sLine);
								request.getSession().setAttribute("lastContent", sLine);
								if ( ((String)hVariables.get("CacheEmails")).compareTo("yes")==0 ) {
									//write email in valid_emails.l file
									synchronized ( objVEaccesSync ) {
										//check first to see if already in valid_emails
										boolean bAlreadyRegistered = false;
										try {
											BufferedReader brVE =new BufferedReader(new FileReader(sConfDir+"valid_emails"+sConfExt));
											do {
												//read line
												sLine = brVE.readLine();
												//check to see if not comment or EOF
												if ( sLine!=null && !sLine.startsWith("#") ) {
													sLine = sLine.trim();
													if ( sLine.compareTo(email)==0 ) {
														bAlreadyRegistered = true;
														break;
													};
												}
											} while ( sLine!=null );
											brVE.close();
										} catch ( FileNotFoundException fnfex) {
											//go quietly
										} catch ( IOException ioex ) {
											//this is a warning
										}
										try {
											//if new email, write it to valid_emails and remove line from confirmations
											if ( !bAlreadyRegistered ) {
												BufferedWriter bwVE =new BufferedWriter(new FileWriter(sConfDir+"valid_emails"+sConfExt, true));
												bwVE.newLine();
												bwVE.write( email);
												bwVE.flush();
												bwVE.close();
											};
										} catch ( IOException ioex ) {
											//this is an writing to file error
											//ignore it
										}
									}
								};
								break;
							}//end code==read code
						}//end nSpace>0
					}//end not vide line and not comment
				} while ( sLine!=null );
				//confirmation ok, so delete it from file
				//for that, close file and open it again
				br.close();
				if ( ret_path!=null && ret_path!="" ) {
					br =new BufferedReader(new FileReader(sConfDir+"confirmations"+sConfExt));
					BufferedWriter bwC =new BufferedWriter(new FileWriter(sConfDir+"~confirmations"+sConfExt, true));
					int nLine2 = 0;
					do {
						sLine = br.readLine();nLine2++;
						if ( sLine!=null && nLine!=nLine2 ) {
							bwC.newLine();
							bwC.write( sLine);
						};
					} while ( sLine!=null );
					bwC.flush();
					bwC.close();
					br.close();
					File fNew = new File( sConfDir+"~confirmations"+sConfExt);
					File fOld = new File( sConfDir+"confirmations"+sConfExt);
					try {
						fNew.renameTo( fOld);
					} catch ( SecurityException sex) {
						//ignore, what to do???
					}
				};
			};
			return ret_path; 
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		return null;
	}

	/**
	 * checks to see if an email is valid based on a cache file if caching is activated
	 * or based on a global variable AnyEmailIsValid
	 * @param email
	 * @return true or false
	 */
	public final boolean checkValidEmail(String email) {
		String sValidE = (String)hVariables.get("AnyEmailIsValid"); 
		if ( sValidE!=null && sValidE.equals("yes") )
			return true;
		String sCacheEmails = (String)hVariables.get("CacheEmails"); 
//		if ( !(sCacheEmails!=null && sCacheEmails.compareTo("yes")==0) )
		if ( sCacheEmails==null || !sCacheEmails.equals("yes") )
			return false;
		if ( email == null )
			return false;
		// 
		try {
			synchronized ( objVEaccesSync ) {
				String sLine;
				BufferedReader br =new BufferedReader(new FileReader(sConfDir+"valid_emails"+sConfExt));
				try{
				    do {
					//read line
					sLine = br.readLine();
					//check to see if not comment or EOF
					if ( sLine!=null && !sLine.startsWith("#") ) {
						sLine = sLine.trim();
						//this is the email address, so compare it with parameter
						if ( sLine.compareTo(email)==0 )
							return true;
					}
				    }
				    while ( sLine!=null );
				}
				finally{
				    br.close();
				}
			};
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		return false;
	}

	/**
	 * Creates a new confirmation for an download.<br>
	 * It generates an unique identifier.<br>
	 * It appends a line in confirmations.l containing this identifier, the email address and the 2 paths.<br>
	 * Then sends a mail to the provided email address with an link that should look like this:<br>
	 * 		http://$site$/$servlet$_link$code$.$anything$<br>
	 * @param email well formed email address that must be confirmed
	 * @param path path to file
	 * @param lastContent path to last content page
	 * @return 	 Returns true if the confirmation has been added in file and email sent,<br>
	 * false in case of an error.<br>  
	 */
	public final boolean newConfirmation(String email, String path, String lastContent) {
		// The unicity is acquired by concatenating date-time-rand
		Calendar c = Calendar.getInstance();
		
		int countValue;
		double randomValue;
		//synchronize access to counter
		synchronized ( objConfSync ) {
			countValue = nConfirmationsCounter;
			nConfirmationsCounter ++;
		};
		StringBuilder sCode1 = new StringBuilder(""+c.getTimeInMillis()+""+countValue);
		StringBuilder sCode2 = new StringBuilder("");
//		StringBuilder sAux;
		for ( int i=0; i<sCode1.length(); i++) {
			randomValue = Math.random();
			/*sAux = */sCode2.append((char)(sCode1.charAt(i)+(randomValue<.33?'A':randomValue<.66?'a':'0')-'0'));
			//if ( sAux != sCode2 )
				//System.out.println("different strings: "+sAux+" <> "+sCode2);
			//sCode2 = sAux;
		};
		//write synchronized a new line in confirmations.l
		synchronized ( objCaccesSync ) {
			try {
				BufferedWriter bw =new BufferedWriter(new FileWriter(sConfDir+"confirmations"+sConfExt, true));
				bw.newLine();
				bw.write( sCode2+" "+email+" "+path+" "+lastContent);
				bw.flush();
				bw.close();
			} catch ( IOException ioex ) {
				//this is an writing to file error
				return false;
			}
		}
		//System.out.println( "send to email: "+email);
		//try to send mail
        try {
    		DirectMailSender dms = DirectMailSender.getInstance();
			dms.sendMessage( (String)hVariables.get("SiteAdmin"), new String[] {email}, "Confirm file download", "Please click on this"
            		+" link to start downloading:\r\n"
					+ sWebPath + sWebResDir+ sServletName+"_link"+sCode2+".html\r\n"
					);
        } catch (Throwable t) {
        	//nothing to see, send mail error
        	//System.out.println(t.getMessage());
        	System.out.println("Error on DirectMailSender to "+email);
        	t.printStackTrace();
        	return false;
        }
		return true;
	}
	
	public final void setSignUpLogOutLink( Page p, int userid)
	{
	    if ( userid!=-1 )
	        p.modify("logout_link","Hi "+((String[])hUsers.get(Integer.valueOf(userid)))[3]+". <a href=\""+sServletName+"_logout.html\">Logout</a>");
		else
		    p.modify("logout_link","<a href=\""+sServletName+"_signup.html\">Sign Up</a>");
	}

	public final void setVariablesOnPage( Page p)
	{
		String sName;
		for ( Iterator it = hVariables.keySet().iterator(); it.hasNext();) {
			sName = (String)it.next();
			p.modify( sName, (String)hVariables.get(sName));
		}
	}
	
	/**
	 * changes text so that special tags are replaced by their values in hVariables
	 * @param text
	 * @return Returns teh updated text
	 */
	public final String setVariablesOnText( String text)
	{
		String sName;
		for ( Iterator it = hVariables.keySet().iterator(); it.hasNext();) {
			sName = (String)it.next();
			text = text.replaceAll("<<:"+sName+":>>", (String)hVariables.get(sName));
		};
		//all special tags remained in text are replaced to ""
		String text2="";
		int nSTag =0, nETag, nSantTag=0;
		while ( nSTag!=-1 ) {
			nSantTag = nSTag;
			nSTag = text.indexOf( "<<:", nSantTag);
			if ( nSTag!=-1 ) {
				nETag = text.indexOf( ":>>", nSTag+3);
				if ( nETag!=-1 )
					text2 += text.substring( nSantTag, nSTag);
					//text = text.substring(0, nSTag)+text.substring(nETag+3);
				nSTag = nETag+3;
			}
		};
		text2 += text.substring( nSantTag);
		return text2;
	}
    
    /**
     * parses a line of text for format like:<br>
     *      attribute = value<br>
     * @param sLine
     * @return array with two elements: name and value
     */
    private String[] readAttribute( String sLine) {
        String []sRet=null;
        if ( sLine!=null && !sLine.startsWith("#") ) {
            int nEqual = sLine.indexOf('=');
            if ( nEqual > 0 ) {
                String sName, sValue;
                sName = sLine.substring( 0, nEqual);
                sName = sName.trim();
                sValue = sLine.substring( nEqual+1);
                sValue = sValue.trim();
                if ( sName.compareTo("")!=0 )
                    sRet = new String[]{sName, sValue};
            }
        }
        return sRet;
    }
	
	public final void readVariables() {
		try {
			String sLine;//, sName, sValue;
			String sAttr[];
			hVariables.clear();//first clear the hashtable of previous uses
			//System.out.println("variables file path: "+sConfDir+"variables"+sConfExt);
			BufferedReader br =new BufferedReader(new FileReader(sConfDir+"variables"+sConfExt));
			do {
				sLine = br.readLine();
                sAttr = readAttribute(sLine);
                if ( sAttr!=null )
                    hVariables.put( sAttr[0], sAttr[1]);
//				if ( sLine!=null && !sLine.startsWith("#") ) {
//					nEqual = sLine.indexOf('=');
//					if ( nEqual > 0 ) {
//						sName = sLine.substring( 0, nEqual);
//						sName = sName.trim();
//						sValue = sLine.substring( nEqual+1);
//						sValue = sValue.trim();
//						if ( sName.compareTo("")!=0 )
//							hVariables.put( sName, sValue);
//					}
//				}
			} while ( sLine!=null );
			
			br.close();
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		/**
		 * add default variables: 
		 * - monsitVersion -> known at build time, generated by ant
		 * - monsitUptime -> found by calling local uptime funtion
		 * - monsitFirstRunTime -> stored in a file from conf, in a variable in ThreadedPage
		 * - monsitRequests -> found by using function from ThreadedPage
		 * - monsitTotalRequests -> stored in another file in conf, strongly connected to first run time
		 * - monsitDate -> known at build time, generated by ant
		 */
		hVariables.put( "monsitVersion", "1.3.1");
		hVariables.put( "monsitUptime", getUptime());
		hVariables.put( "monsitFirstRunTime", getFirstRunDate());
		hVariables.put( "monsitRequests", ""+getRequestCount());
		hVariables.put( "monsitTotalRequests", ""+getTotalRequests());
		hVariables.put( "monsitDate", "2016-11-24 09:06");
	}
	
	/**
	 * reads the users file organized as:<br>
	 * user_id user password email name
	 * @returns fills a map with user_id -> String[]={user,password,email,name}
	 */
	public final void readUsers() {
		try {
			String sLine;
			int nSpace;
			hUsers.clear();//first clear the hashtable of previous uses
			BufferedReader br =new BufferedReader(new FileReader(sConfDir+"users"+sConfExt));
			do {
				sLine = br.readLine();
				try {
					if ( sLine!=null && !sLine.startsWith("#") ) {
						//get id
						sLine = sLine.trim();
						nSpace = sLine.indexOf(' ');
						if ( nSpace > 0 ) {
							Integer userID = Integer.valueOf(sLine.substring(0,nSpace));
							sLine = sLine.substring(nSpace);
							sLine = sLine.trim();
							//get user data
							String []userData = new String[4];
							for( int i=0;i<3; i++) {
								nSpace = sLine.indexOf( ' ');
								if ( nSpace > 0) {
									userData[i] = sLine.substring(0, nSpace);
									sLine = sLine.substring(nSpace);
									sLine = sLine.trim();
								} else
									userData[i] = "";
							};
							userData[3] = sLine;
							hUsers.put( userID, userData);
						}
					}
				} catch ( NumberFormatException nfex) {
					//go to next line
				}
			} while ( sLine!=null );
			
			br.close();
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
	}
	
	/**
	 * reads a property given its name from a file<br>
	 * it accomplish that by searching the entire file
	 * @return empty string if nothing matched the property name or the found value
	 */
	public final String readPropertyFromFile(String propName, String filePath)
	{
		String propValue = "";
		try {
			String sLine, sName;
			int nEqual;
			BufferedReader br =new BufferedReader(new FileReader(filePath));
			do {
				sLine = br.readLine();
				if ( sLine!=null && !sLine.startsWith("#") ) {
					sLine = sLine.trim();
					nEqual = sLine.indexOf('=');
					if ( nEqual > 0 ) {
						sName = sLine.substring( 0, nEqual);
						sName = sName.trim();
						if ( sName.compareTo(propName)==0 ) {
							propValue = sLine.substring( nEqual+1);
							propValue = propValue.trim();
							break;
						};
					}
				}
			} while ( sLine!=null );
			
			br.close();
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		return propValue;
	}

	/**
	 * reads a set of properties given in first parameter, and replaces them with their values
	 * @param props
	 * @param filePath
	 * @return nothing, as the in vector is also out value
	 */
	public final void readPropertiesFromFile(String[] props, String filePath)
	{
		int checked=0;//each bit checkes a property, that means that there can be only... sizeof(checked)*8 properties
		int sizeof_int = 4;
		try {
			String sLine, sName;
			int nEqual;
			BufferedReader br =new BufferedReader(new FileReader(filePath));
			do {
				sLine = br.readLine();
				if ( sLine!=null && !sLine.startsWith("#") ) {
					sLine = sLine.trim();
					nEqual = sLine.indexOf('=');
					if ( nEqual > 0 ) {
						sName = sLine.substring( 0, nEqual);
						sName = sName.trim();
						for( int i=0; i<props.length && i<sizeof_int*8; i++) {
							if ( sName.compareTo(props[i])==0 && ((checked>>i)&1)==0 ) {
							    checked |= (1<<i);
								props[i] = sLine.substring( nEqual+1);
								props[i] = props[i].trim();
								break;
							};
						};
					}
				}
			} while ( sLine!=null );
			
			br.close();
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		//keys not found in file are defaulted to ""
		for( int i=0; i<props.length && i<sizeof_int*8; i++)
		    if ( ((checked>>i)&1)==0 )
		        props[i]="";
		//return propValue;
	}
	
	
	/**
	 * checks to see if a user has access to a file.<br>
	 * For that, reads the files.l file that has grant access for a file to a user
	 * and has the syntax:<br>
	 * path user_ids
	 * @param usrID id of user who wants to check this page, or 0 for no user id
	 * @param path path to file
	 * @return Returns an integer code, meaning:<br>
	 * 2 path found, email access granted <br>
	 * 1 path found, user granted access for file<br>
	 * 0 path found, no user access neccessary - usefull for counting hits, or logging<br>
	 * -1 path not found<br>
	 * -2 path found, user doesn't have access<br>
	 * -3 path found, user doesn't have access and invalid email.<br>
	 * -4 path found, only email access valid.<br>
	 */
	public final int checkUserAccessForFile( int usrID, String path)
	{
		try {
			String sLine,  sPath;
			int nSpace;
			BufferedReader br =new BufferedReader(new FileReader(sConfDir+"files"+sConfExt));
			try{
			    do {
				//read line
				sLine = br.readLine();
				//check to see if not comment or EOF
				if ( sLine!=null && !sLine.startsWith("#") ) {
					
					sLine = sLine.trim();
					//get path
					nSpace = sLine.indexOf(' ');
					if ( nSpace > 0 ) {
						sPath = sLine.substring( 0, nSpace);
						sLine = sLine.substring(nSpace);
						sLine = sLine.trim();
					} else {
						sPath = sLine;
						sLine = "";
					};
					//replace any special tag in path:
					//System.out.println("path before: "+sPath);
					sPath = setVariablesOnText(sPath);
					//System.out.println("path after: "+sPath);
					//if this is searched path
					//replace equal sign with startsWith to apply a folder's rule to its files and subfolders
					//if ( path.compareTo( sPath) == 0 ) {
					if ( sPath.compareTo("")== 0 )
						continue;
					//System.out.println("path="+path+" linePath="+sPath+" path.startsWith( sPath)="+path.startsWith( sPath));
					if ( path.startsWith( sPath) ) {
						//if to check the user then
						//if ( usrID> 0 ) {//usrID =0 means no user to check for
						boolean bHasUsers = false;
						int uid;
						String[] uids = sLine.split( " ");
						for ( int i=0; i<uids.length; i++) {
							try {
					 			if ( uids[i].compareTo("")!=0 ) {
					 				uid = Integer.parseInt(uids[i]);
					 				if ( uid == usrID)
					 					return 1;//found user, access granted
					 				else if ( uid == -2 && hUsers.containsKey( Integer.valueOf(usrID)) ) {//all users have access
					 					//System.out.println("path: "+path+" user access=all users, user id="+usrID);
					 					return 1;
					 				}
					 				bHasUsers = true;
					 			};
							} catch ( NumberFormatException nfex) {
								//skip this mallformed
								//see if is identifier for mail logon
								int nIsEmail = 0;
								if ( uids[i].compareTo("e")==0 )
									nIsEmail = 1;
								else if ( uids[i].compareTo("o")==0 )
									nIsEmail = 2;
								if ( nIsEmail > 0 ) {
									String sEmail = (String)request.getSession().getAttribute("email");
									if ( sEmail == null ) sEmail = "";
									//if logged as a user or has valid email, accept
									if ( hUsers.containsKey( Integer.valueOf(usrID)) || sEmail.compareTo("")!=0 )
										return 2;
									else if ( nIsEmail == 2 )
										return -4; //only email possible acces
									else
										return -3; //email or user possible access
								};
							}
						}
						if ( bHasUsers )//this page requires authentication, but this user is not on the list
							return -2;//no access granted for this user
						//}
						return 0;//path found, no user to check for it, so everyting ok
					}
				}
			} while ( sLine!=null );
		    }
		    finally{
			br.close();
		    }
		} catch ( FileNotFoundException fnfex) {
			//go quietly
		} catch ( IOException ioex ) {
			//this is a warning
		}
		return -1;//path not found
	}
	
	public final void writeLineInLog( String path, String userID)
	{
		try {
			BufferedWriter bw =new BufferedWriter(new FileWriter(sLogDir+sLogFile, true));
			bw.write( path+" "+showDate(new Date())+" ");
			Integer UID;
			//get uid
			try {
				UID = Integer.valueOf(Integer.parseInt( userID));
			} catch (NumberFormatException nfex) {
				//corect uid
				UID= Integer.valueOf(0);
			};
			//check uid, and get info
			if ( hUsers.containsKey( UID)) {
				String[] uInfo = (String[])hUsers.get(UID);
				bw.write( uInfo[3]+" "+uInfo[2]+" "+uInfo[0]+" ");
			} else { //check if email address available
				String sEmail;
				sEmail = (String)request.getSession().getAttribute("email");
				if ( sEmail == null )
					sEmail = "";
				bw.write( sEmail+" ");
			};
			//write remote access info
			//remote access host found through header info because the is an apache
			//server that is used as proxy to send user requests to servlet
			bw.write( request.getHeader("x-forwarded-for"));//request.getRemoteAddr()+" "+request.getRemoteHost());
			bw.newLine();
			bw.flush();
			bw.close();
		} catch ( IOException ioex ) {
			//this is a warning
		}
	}
	
	/**
	 * modifies the list received as parameter to correspond to properties
	 * read from .properties file in the supplied directory path.
	 * @param list
	 * @param propFileDir
	 */
	public final void readListProperties( Vector list, String propFileDir)
	{
		if ( list==null )
			return;
        //UPDATE at 17 June 2005
        //read properties file for each option in menu to customize it further
        //this only updates info in allList structure
        /**
         * read properties algorithm:<br>
         * read each line in file, determin the attribute and value and set it for the current element.<br>
         * The order atribute is an exception because it requires several other steps. 
         */
        MenuOptionParams current_mop, mop;
        int i=0, current_id;
        Object obj;
        File fis;
        fis = new File( propFileDir+".properties");
        if ( fis.isFile() ) {
            try {
                current_mop=null;//no current option for the moment
                current_id = -1;
                String sLine;
                BufferedReader br =new BufferedReader(new FileReader(fis));
                String sAttr[];
                do {
                    sLine = br.readLine();
                    sAttr = readAttribute(sLine);
                    if ( sAttr!=null ) {
                        //check if attribute is one of the supported ones, and value is coherent with the attribute's type
                        for ( i=0; i<sPropsAttributes.length; i++)
                            if ( sAttr[0].equals(sPropsAttributes[i]))
                                break;
                        //if unrecognizable attribute, skip it
                        if ( i>=sPropsAttributes.length )
                            continue;
                        //validate the value
                        if ( sAttr[0].equals("target") )
                            if ( !sAttr[1].equals("_blank") && !sAttr[1].equals("_parent") && !sAttr[1].equals("_self") && !sAttr[1].equals("_top") )
                                continue;
                        if ( sAttr[0].equals("order") ) {
                            try {
                                /*int pos = */Integer.parseInt(sAttr[1]);
                            } catch(NumberFormatException nfex) {
                                continue;
                            }
                        }
                        //set attribute for option element
                        //if attribute is name, set new current option
                        if ( sAttr[0].equals("name") ) {//set new current option element for whom to read attributes
                            for ( i=0; i<list.size(); i++) {
                                mop = (MenuOptionParams)list.get(i);
                                if ( sAttr[1].equals( mop.name) ) {
                                    current_mop = mop;
                                    current_id = i;
                                    break;
                                };
                            };
                            //if no option has this name, skip it
                            if ( i>=list.size() ) {
                                current_mop = null;
                                current_id = -1;
                            };
                        } else if ( current_mop!=null && current_id!=-1 ) {//set attribute for current element if one is selected
                            //check first if order attribute
                            if ( sAttr[0].equals("order") ) {
                                int pos=0;
                                //check if valid
                                try {
                                    pos = Integer.parseInt(sAttr[1]);
                                } catch(NumberFormatException nfex) {
                                    //should not get here
                                    continue;
                                }
                                //remove element from list
                                list.remove(current_id);
                                //put it where it should
                                for ( i=0; i<list.size(); i++) {
                                    mop = (MenuOptionParams)list.get(i);
                                    obj = mop.properties.get("order");
                                    if ( obj!=null ) {
                                        try {
                                            int new_pos = Integer.parseInt(obj.toString());
                                            //if position of this element is greater than of the one to be inserted, exit the for
                                            //and insert it
                                            if ( new_pos>pos )
                                                break;
                                        } catch(NumberFormatException nfex) {
                                            break;//invalid order number, so insert before this
                                        }
                                    } else {
                                        break;//no order attribute, so insert in front of this element
                                    }
                                    //else go to next element
                                }
                                //reposition element in list
                                if ( i> list.size() )
                                    current_id = list.size();
                                else
                                    current_id = i;
                                list.add( current_id, current_mop);
                            } else {
                                //set attribute to option element
                                current_mop.properties.put( sAttr[0], sAttr[1]);
                            }
                        }//end if current_mop!=null
                    }//end if attributes not null
                } while ( sLine!=null );
                br.close();
            } catch ( FileNotFoundException fnfex) {
                //go quietly
            } catch ( IOException ioex ) {
                //this is a warning
            }
        }
	}
	
	/**
	 * sorts entries in unsortedList based on .order file found in given directory.<br>
	 * If the file is not present, the vector is not sorted.<br>
	 * The sorting is based on entries found in file that are compared to the name
	 * field of each element in list. An element must be of MenuOptionParams type.
	 * @param unsortedList
	 * @param orderFileDir
	 * @return
	 */
	public final Vector orderList( Vector unsortedList, String orderFileDir)
	{
		File fis;
		//check to see if there is an order file available
		fis = new File( orderFileDir+".order");
		if ( fis.isFile() ) {
			//System.out.println("found .order file in "+orderFileDir);
			//construct the auxiliary vector
			Vector orderList = new Vector();
			//read a line and search for entry in allList
			try {
				String sLine;
				BufferedReader br =new BufferedReader(new FileReader(fis));
				do {
					sLine = br.readLine();
					try {
						if ( sLine!=null && !sLine.startsWith("#") ) {
							sLine = sLine.trim();
							for( int i=0; i<unsortedList.size(); i++)
								if ( sLine.equals( ((MenuOptionParams)unsortedList.get(i)).name) )
									orderList.add( unsortedList.get(i));
						}
					} catch ( NumberFormatException nfex) {
						//go to next line
					}
				} while ( sLine!=null );
				//replace list of all options with ones that are in .order
				unsortedList = orderList;
				br.close();
			} catch ( FileNotFoundException fnfex) {
				//go quietly
			} catch ( IOException ioex ) {
				//this is a warning
			}
		}
		//return ordered list if .order file was found
		return unsortedList;
	}
	
	/**
	 * orders files in fList by modify time asc or desc, depending on param
	 * @param fList
	 * @param desc If true, order descendent, else order ascendent.
	 * @return Nothing, as all modifications are made inside the vector.
	 */
	public final void orderFileList( File[] fList, final boolean desc)
	{
		if ( fList == null )
			return;
		Arrays.sort( fList, new Comparator() {
			public int compare(Object a, Object b) {
				if ( !(a instanceof File) )
					return desc?-1:1;
				if ( !(b instanceof File) )
					return desc?1:-1;
				File filea = (File)a;
				File fileb = (File)b;
				if ( desc ) {
					if ( filea.lastModified() > fileb.lastModified() )
						return 1;
					else
						return -1;
				} else {
					if ( filea.lastModified() > fileb.lastModified() )
						return -1;
					else
						return 1;
				}
			}
		});
	}
	
/*	public final String doMenu(String current_path, String abs_path)
	{
		String menu="";
		String dir_img;
		if ( current_path.compareTo(abs_path) == 0 )
			dir_img = "<image src=img/folderopen.gif align=absmiddle>";
		else dir_img = "<image src=img/folder.gif align=absmiddle>";
		menu += "<table border=0 cellspacing=0 cellpadding=0><tr><td>"+dir_img+"</td><td><b>"+getMenuOptionName(current_path)+"</b></td><tr>";
		File fis = new File( current_path);
		File[] fileList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				if ( pathname.isFile() && pathname.getName().compareTo("content.res")!=0 )
					return true;
				return false;
			}
		});
		File[] dirList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				if ( pathname.isDirectory() )
					return true;
				return false;
			}
		});
		//put files in menu
		for ( int i=0; i<fileList.length; i++ ) {
			menu += "<tr><td>";
			//set an continuing down vertical bar
			//if ( i!=fileList.length-1 || dirList.length!=0 )
				//menu+="|";
			menu+="-"+"</td><td><img src=img/page.gif align=absmiddle>&nbsp;<b>"+getMenuOptionName(current_path+"/"+fileList[i].getName())+"</b></td></tr>";
		}
		//put directories in menu, recursively
		for ( int i=0; i<dirList.length; i++ ) {
			menu += "<tr><td valign=top>";
			//set an continuing down vertical bar
			//if ( i!=dirList.length-1 )
				//menu+="|";
			menu+="-"+"</td><td><b>"+doMenu( current_path+"/"+dirList[i].getName(), abs_path)+"</b></td></tr>";
		}
		menu += "</table>";
		return menu;
	}
*/	
/*	private int index_selected;//index for selected item in menu
	class MenuObj
	{
		public int index;
		public String menu;
		public MenuObj()
		{
			index = 0;
			menu = "";
		}
	}
*/	
	/**
	 * constructs a dynamic javascript menu, based on directory structure on disk,
	 * and selects an item in menu based on its path
	 * @param current_path current path this recursion has got to
	 * @param abs_path absolute path received as parameter for page
	 * @param index index for option in javascript menu
	 * @param parent_index index of parent item in javascript menu
	 * @return the partial generated menu
	 */
/*	public final MenuObj doMenu2(String current_path, String abs_path, int index, int parent_index)
	{
		File fis;
		MenuObj mobj = new MenuObj(), auxMO;
		//String menu="";
		if ( current_path.compareTo(abs_path) == 0 )
			index_selected = index;
		//add a directory to javascript menu
		mobj.menu += "d.add("+index+","+parent_index+",'"+getMenuOptionName(current_path)+"','";
		fis = new File( sContentDir+current_path+(current_path.endsWith("/")?"":"/")+"content.res");
		System.out.println("foder path is: "+sContentDir+current_path+(current_path.endsWith("/")?"":"/")+"content.res");
		if ( fis.isFile() )
			mobj.menu += "http://se.rogrid.pub.ro:8080/servlet/monsit?path="+current_path;
		mobj.menu += "');\r\n";
		fis = new File( sContentDir+current_path);
		File[] fileList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				if ( pathname.isFile() && pathname.getName().compareTo("content.res")!=0 )
					return true;
				return false;
			}
		});
		File[] dirList = fis.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				if ( pathname.isDirectory() )
					return true;
				return false;
			}
		});
		int next_index = index+1;
		//put directories in menu, recursively
		if ( dirList!= null )
			for ( int i=0; i<dirList.length; i++ ) {
				auxMO = doMenu2( current_path+(current_path.endsWith("/")?"":"/")+dirList[i].getName(), abs_path, next_index, index);
				mobj.menu += auxMO.menu;
				next_index = auxMO.index; //get the next available index
			}
		String filepath, optionname;
		//put files in menu
		if ( fileList!=null )
			for ( int i=0; i<fileList.length; i++ ) {
				filepath = current_path+(current_path.endsWith("/")?"":"/")+fileList[i].getName();
				if ( filepath.compareTo(abs_path) == 0 )
					index_selected = next_index;
				optionname = getMenuOptionName(filepath);
				//construct as link
				filepath =  "http://se.rogrid.pub.ro:8080/servlet/monsit?path="+filepath;
				mobj.menu += "d.add("+next_index+","+index+",'"+optionname+"','"+filepath+"','"+optionname+"');\r\n";
				next_index ++;
			}
		mobj.index = next_index;
		return mobj;
	}
*/
}
