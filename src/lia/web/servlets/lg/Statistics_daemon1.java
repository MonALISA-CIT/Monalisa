package lia.web.servlets.lg;

import java.awt.Color;
import java.awt.Paint;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import lia.Monitor.Store.Cache;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.Page;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.servlet.ServletUtilities;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class Statistics_daemon1 extends HttpServlet {

    // Through this object you will access the history data
    private TransparentStoreFast store;
    public static final Paint ML_BACKGROUND_PAINT = Color.WHITE;
    String sResDir = "";
    String sConfDir = "";
    
    public static int nrRep = 0;
    public static int nrProxies = 0;
    public static int nrClients = 0;
    public static int nrFarms = 0;
    public static int nrNodes = 0;
    public static int nrParams = 0;
    public static QueryResult recResult;
    public static QueryResult recMsg;
    public static QueryResult sentMsg;
    public static String fileNamePageViews="";
    
    private JFreeChart XYSeriesDemo(String title, Vector v) {
    	
    	TimeSeries series = new TimeSeries(title,Hour.class);
	
	Hour h= new Hour();
	Day d= new Day();
    	series.add(new Hour(h.getHour()-1,d), ((Integer) v.get(0)).intValue());
    	series.add(new Hour(h.getHour()-2,d), ((Integer) v.get(1)).intValue());
    	series.add(new Hour(h.getHour()-3,d), ((Integer) v.get(2)).intValue());
    	series.add(new Hour(h.getHour()-4,d), ((Integer) v.get(3)).intValue());
    	series.add(new Hour(h.getHour()-5,d), ((Integer) v.get(4)).intValue());
    	series.add(new Hour(h.getHour()-6,d), ((Integer) v.get(5)).intValue());
    	series.add(new Hour(h.getHour()-7,d), ((Integer) v.get(6)).intValue());
    	series.add(new Hour(h.getHour()-8,d), ((Integer) v.get(7)).intValue());
    	series.add(new Hour(h.getHour()-9,d), ((Integer) v.get(8)).intValue());
    	series.add(new Hour(h.getHour()-10,d), ((Integer) v.get(9)).intValue());
    	series.add(new Hour(h.getHour()-11,d), ((Integer) v.get(10)).intValue());
    	series.add(new Hour(h.getHour()-12,d), ((Integer) v.get(11)).intValue());
	series.add(new Hour(h.getHour()-13,d), ((Integer) v.get(12)).intValue());
    	series.add(new Hour(h.getHour()-14,d), ((Integer) v.get(13)).intValue());
    	series.add(new Hour(h.getHour()-15,d), ((Integer) v.get(14)).intValue());
    	series.add(new Hour(h.getHour()-16,d), ((Integer) v.get(15)).intValue());
    	series.add(new Hour(h.getHour()-17,d), ((Integer) v.get(16)).intValue());
    	series.add(new Hour(h.getHour()-18,d), ((Integer) v.get(17)).intValue());
    	series.add(new Hour(h.getHour()-19,d), ((Integer) v.get(18)).intValue());
    	series.add(new Hour(h.getHour()-20,d), ((Integer) v.get(19)).intValue());
    	series.add(new Hour(h.getHour()-21,d), ((Integer) v.get(20)).intValue());
    	series.add(new Hour(h.getHour()-22,d), ((Integer) v.get(21)).intValue());
    	series.add(new Hour(h.getHour()-23,d), ((Integer) v.get(22)).intValue());
    	series.add(new Hour(h.getHour()-24,d), ((Integer) v.get(23)).intValue());
    	
	TimeSeriesCollection data = new TimeSeriesCollection(series);
    	
    DateAxis xAxis = new DateAxis("");
    NumberAxis yAxis = new NumberAxis(title);
    	
    XYItemRenderer renderer = new StandardXYItemRenderer(StandardXYItemRenderer.LINES);
    XYPlot plot = new XYPlot(data, xAxis, yAxis, renderer);
    renderer.setToolTipGenerator(new StandardXYToolTipGenerator());
    plot.setOrientation(PlotOrientation.VERTICAL);
   	  
	int iOption = StandardXYItemRenderer.SHAPES_AND_LINES;
	
	StandardXYItemRenderer xyir = new StandardXYItemRenderer(iOption, null, null);
	
	XYPlot chartPlot = new XYPlot(data, new DateAxis(""), new NumberAxis("Served Pages"), xyir);
	ValueAxis axis = (ValueAxis) chartPlot.getDomainAxis();
	((DateAxis) axis).setDateFormatOverride(new SimpleDateFormat("MMM d, HH:mm" ));
	
	JFreeChart chart1 = new JFreeChart(
	    "Pages Served by Repositories in the last 24h", 
	    JFreeChart.DEFAULT_TITLE_FONT, 
	    chartPlot, 
	    true
	   );
	   
	chart1.setBackgroundPaint(ML_BACKGROUND_PAINT);
	return chart1;

    }
    
    
    //------------------------------------------------------- SERVLET -------------------------------------
	public static void copy(String source, String dest) throws IOException {
		FileChannel in = null, out = null;
		try {          
			in = new FileInputStream(source).getChannel();
			out = new FileOutputStream(dest).getChannel();
		
			long size = in.size();
			MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
		
			out.write(buf);
		
		} finally {
			if (in != null)          in.close();
			if (out != null)     out.close();
		}
	}
    
    public static Object objSync = new Object();
    
    private void copyUrl2File( String sUrl, String sDir, String sFileName)
    {
    	synchronized( objSync) {
	try{
		URL url = new URL(sUrl);
		BufferedReader br = new BufferedReader( new InputStreamReader(url.openStream()));
		BufferedWriter bw = new BufferedWriter(new FileWriter(sDir+sFileName, false));
		String sLine;
		int nFoundImage=-1;
		while( (sLine=br.readLine())!=null ) {
			nFoundImage = sLine.indexOf("display?image=");
			if ( nFoundImage!=-1 ) {
				String sImageName;
				int nLastChar = sLine.indexOf( "\" ", nFoundImage);
				if ( nLastChar!=-1 ) {
					sImageName = sLine.substring( nFoundImage+14, nLastChar);
					System.out.println("[copyUrl2File 1] for "+sFileName+" image name found is: "+sImageName);
					copy( System.getProperty("java.io.tmpdir") + "/" + sImageName, sDir + sImageName);
					System.out.println("[copyUrl2File 2] initial line; "+sLine);
					sLine = sLine.substring( 0, nFoundImage) + sLine.substring(nFoundImage+14);
					System.out.println("[copyUrl2File 3] altered line: "+sLine);
				}
			}
			bw.write(sLine);
			bw.newLine();
		}
		br.close();
		bw.flush();
		bw.close();
	} catch (Exception ex) {
		ex.printStackTrace();
	}
	};
    }
    

    public void doGet(final HttpServletRequest req,HttpServletResponse resp) throws IOException {
	ServletContext sc = getServletContext();

    	sResDir = sc.getRealPath("/");
    	if (!sResDir.endsWith("/"))
    		sResDir += "/";
	sResDir +="LookingGlass/";

	try{
	    // get an instance of the store
	    store = (TransparentStoreFast) TransparentStoreFactory.getStore();
	}
	catch (Exception e){
	    return;
	}	
	
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000&dont_cache=true", sResDir, "view001.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000&dont_cache=true", sResDir, "view002.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000&dont_cache=true", sResDir, "view003.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs&interval.min=7257600000&res_path=new_res&dont_cache=true", sResDir, "view004.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res&dont_cache=true", sResDir, "view005.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages&displaysum=true&sum=1&res_path=new_res&interval.min=7257600000&dont_cache=true&skipnull=1&skipfactor=1&gap_if_no_data.area=false", sResDir, "view006.html");
	copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=7257600000&dont_cache=true", sResDir, "view007.html");
	    
	    
	 
	 File f = new File(sResDir+"statistics_out1.html");
	 FileOutputStream fos = new FileOutputStream(f);     
	 Page statPage = new Page(fos,sResDir + "statistics_new.html");
	 
	 
	 
	 
	 try{
	 
	//-------------------------------- TOTAL PROXIES -------------------------
	
	int nrProxies=0;
	
	monPredicate p = new monPredicate(
			"MLRepos",
			"farm_proxy",
			"%",
			-1,-1,
			new String[]{"nrClients"},
			null
	);	
	
	Result r;
	Vector v = Cache.getLastValues(p);
	
	nrProxies += v.size();
	
	p = new monPredicate(
			"MLRepos",
			"vrvs_proxy",
			"%",
			-1,-1,
			new String[]{"nrClients"},
			null
	);	
	
	v = Cache.getLastValues(p);
	
	nrProxies += v.size();
	
	

	//------------------------------ TOTAL REPOSITORIES --------------------------------
			
	int nrRep=0;
	p = new monPredicate(
			"MLRepos",
			"Repository",
			"%",
			-1,-1,
			new String[]{"Uptime"},
			null
	);	
	
	v = Cache.getLastValues(p);
	
	nrRep = v.size();
	
	//---------------------------------- TOTAL CLIENTS ---------------------------------
	
	int nrClients = 0;
	
	p = new monPredicate(
			"MLRepos",
			"farm_proxy",
			"%",
			-1,-1,
			new String[]{"nrClients"},
			null
	);			
	
	v = Cache.getLastValues(p);
	for(int i=0; i< v.size(); i++){
		r = (Result) v.get(i);
		nrClients += r.param[0];
	}
	
	//-------------------------------------------------------- TOTAL FARMS ----------------------------------------
	
	int nrFarms = 0;
	p = new monPredicate(
			"MLRepos",
			"farm_proxy",
			"monalisa.cacr.caltech.edu",
			-1,-1,
			new String[]{"farmsNr"},
			null
	);			
	r = (Result)Cache.getLastValue(p);
	if (r != null)
		nrFarms += r.param[0];
	
	p = new monPredicate(
			"MLRepos",
			"vrvs_proxy",
			"monalisa-chi.uslhcnet.org",
			-1,-1,
			new String[]{"farmsNr"},
			null
	);			
	r = (Result)Cache.getLastValue(p);
	if (r != null)
		nrFarms += r.param[0];
	
	//----------------------------------------------------------- TOTAL NODES -------------------------------------------
	
	p = new monPredicate(
			"MLRepos",
			"farm_proxy",
			"%",
			-1,-1,
			new String[]{"total_nodes"},
			null
	);			
	
	int nrNodes = 0;
	Result  nodes_result = (Result)Cache.getLastValue(p);
	if (nodes_result != null)
		 nrNodes += nodes_result.param[0];
	
	p = new monPredicate(
			"MLRepos",
			"vrvs_proxy",
			"monalisa-chi.uslhcnet.org",
			-1,-1,
			new String[]{"total_nodes"},
			null
	);			
	
	nodes_result = (Result)Cache.getLastValue(p);
	if (nodes_result != null)
		nrNodes += nodes_result.param[0];
	
	//---------------------------- TOTAL PARAMS --------------------------------------------------------------------------
	
	p = new monPredicate(
			"MLRepos",
			"farm_proxy",
			"monalisa-ul.caltech.edu",
			-1,-1,
			new String[]{"total_params"},
			null
	);			
	
	int nrParams = 0;
	Result  params_result = (Result)Cache.getLastValue(p);
	if (params_result != null)
		nrParams += params_result.param[0];
	
	p = new monPredicate(
			"MLRepos",
			"vrvs_proxy",
			"monalisa-chi.uslhcnet.org",
			-1,-1,
			new String[]{"total_params"},
			null
	);			
	
	params_result = (Result)Cache.getLastValue(p);
	if (params_result != null)
		nrParams += params_result.param[0];
	
	 
	//------------------------------- RESULTS & MESSAGES ------------------------------------------	 
	
		
	QueryResult recResult = queryStatistics("ReceivedResults");
	QueryResult recMsg = queryStatistics("nrReceivedMsg");
	QueryResult sentMsg = queryStatistics("nrSentMsg");	
		


	//--------------------------- write to page ------------------------------------------------		
		
	statPage.modify("nrRep",nrRep);
	statPage.modify("nrProxies",nrProxies);
	statPage.modify("nrClients",nrClients);
	statPage.modify("nrFarms",nrFarms);
	statPage.modify("nrNodes",nrNodes);
	statPage.modify("nrParams",nrParams);
	
	
	
	statPage.modify("recResult.res1min",recResult.res1min);
	statPage.modify("recResult.res1h",recResult.res1h);
	statPage.modify("recResult.res24h",recResult.res24h);
	statPage.modify("recResult.res1min-av",recResult.res1min/60);
	statPage.modify("recResult.res1h-av",recResult.res1h/3600);
	statPage.modify("recResult.res24h-av",recResult.res24h/86400);
	statPage.modify("passedMsg.res1min",recMsg.res1min + sentMsg.res1min);
	statPage.modify("passedMsg.res1h",recMsg.res1h + sentMsg.res1h);
	statPage.modify("passedMsg.res24h",recMsg.res24h + sentMsg.res24h);
	statPage.modify("passedMsg.res1min-av",(recMsg.res1min + sentMsg.res1min)/60);
	statPage.modify("passedMsg.res1h-av",(recMsg.res1h + sentMsg.res1h)/3600);
	statPage.modify("passedMsg.res24h-av",(recMsg.res24h + sentMsg.res24h)/86400);
	
	
	String fileNamePageViews="";
	HttpSession session = req.getSession(true);
	Vector pageViews = queryPageViews("Requests_permin");
	
	JFreeChart pageSeries = XYSeriesDemo("Requests per Hour", pageViews);
	
	try{	
		fileNamePageViews = ServletUtilities.saveChartAsPNG(pageSeries, 335, 285, session);
	}catch(IOException e){
		e.printStackTrace();
	}
	
	copy( System.getProperty("java.io.tmpdir") + "/" + fileNamePageViews, sResDir + "view008.png");
	statPage.modify("fileNamePageViews",fileNamePageViews);
	
	statPage.write();
    	}catch(Throwable t){
    		System.err.println(t.toString());
    		t.printStackTrace();
    	}   
	    
	
	
    }	
	//------------------------------------------ QUERY FUNCTOINS --------------------------
	
    public Vector queryPageViews(String s){
    	Vector resultVector = new Vector(24);
    	monPredicate p;
    	Vector vData;
    	ExtendedResult er;
    	int total_page_views_h;
    	
    	for(int j=1; j<=24 ; j++){
    		p = new monPredicate(
    				"MLRepos",
    				"Repository",
					"%",
					-j*60*60*1000,-(j+1)*60*60*1000,
					new String[]{s},
					null
    		);			
    		
    		vData = store.select(p);
    		total_page_views_h = 0;
    		for (int i=0; i<vData.size(); i++){
    			er = (ExtendedResult) vData.get(i);
    			total_page_views_h += er.param[0];
    		}
    		resultVector.add(Integer.valueOf(total_page_views_h));	
    		
    	}
    	return resultVector;
    }
    
    public QueryResult queryStatistics(String s){
	    
	    DB db = new DB();
	    QueryResult qr = new QueryResult();
	    Result r;
	    Vector vData;
	    ExtendedResult er;
	    monPredicate p;		    	    
	    
	    db.query("select distinct (mi_key) from monitor_ids where mi_key like '%/"+s+"';");
	
	    int total_received_results_1h = 0;
	    int total_received_results_24h = 0;
	    int total_received_results_1month = 0;
	    int total_received_results_1min = 0;
	
	    	
	    while(db.moveNext()){ 
	    		p = new monPredicate(
	    					(db.gets(1)).split("/",4)[0],
	    					(db.gets(1)).split("/",4)[1],
						(db.gets(1)).split("/",4)[2],
						-60*1000,-1,
						new String[]{s},
						null
	    		);
	    		r = (Result)Cache.getLastValue(p);
	    		if (r != null)
	    			total_received_results_1min += r.param[0];
	    
	    
			p = new monPredicate(
						"MLRepos",
						(db.gets(1)).split("/",4)[1],
						(db.gets(1)).split("/",4)[2],
						-60*60*1000,-1,
						new String[]{s},
						null
			);
			vData = store.select(p);
			for (int i=0; i<vData.size(); i++){
			    er = (ExtendedResult) vData.get(i);
			    total_received_results_1h += er.param[0];
			}
		
		
			p = new monPredicate(
	    					"MLRepos",
						(db.gets(1)).split("/",4)[1],
						(db.gets(1)).split("/",4)[2],
						-24*60*60*1000,-1,
						new String[]{s},
						null
			);																	
			vData = store.select(p);
			for (int i=0; i<vData.size(); i++){
			    er = (ExtendedResult) vData.get(i);
			    total_received_results_24h += er.param[0];
			}
			
			
	    }				    
	    qr.res1min = total_received_results_1min;
	    qr.res1h = total_received_results_1h;
	    qr.res24h = total_received_results_24h;
	    qr.res1month = total_received_results_1month;
	    
	    return qr;
	}
    	
}
