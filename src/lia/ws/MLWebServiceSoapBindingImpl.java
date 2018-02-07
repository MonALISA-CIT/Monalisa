package lia.ws;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreInt;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author mickyt
 *
 */
public class MLWebServiceSoapBindingImpl implements lia.ws.MLWebService {

    static final int MAX_NR_OF_CONFIGURATIONS = 100;

    private static TransparentStoreInt _store = null;
	
	private static SimpleDateFormat sdf = null;

    static {
        try {
            _store = TransparentStoreFactory.getStore();
			sdf = new SimpleDateFormat ();
			sdf.applyPattern("yyyy-MM-dd'T'HH:mm:ssZ") ;
        } catch (Exception ex) {
            System.err.println(" MLWebService =======> CANNOT INSTANTIATE STATIC STORE");
            ex.printStackTrace();
        }
    } // static

    public static final String[] vsCounterNames = new String[] { "getConfiguration", "getLatestConfiguration",
            "getValues", "getLastValues", "getFilteredLastValues"};

    public static long[] vsCounterValues = new long[] { 0, 0, 0, 0, 0};

    public HashMap networkMeasurementSet (HashMap request) throws java.rmi.RemoteException, org.apache.axis.AxisFault {
    
	    if (_store == null) { throw new org.apache.axis.AxisFault("Database Error"); }
		
		if (request == null) {
			throw new org.apache.axis.AxisFault ("Wrong request => null") ;
		} // if
		
		String source = null ;
		String dest = null ;
		
		String startTime = null ;
		long mlStartTime = 0;
		
		String endTime = null ;
		long mlEndTime = 0;
		
		String characteristic = null ; 
		
		//get networkCharacteristic.
		
		
		characteristic = (String)request.get("networkCharacteristic") ;
		if (characteristic == null) {
			throw new org.apache.axis.AxisFault ("No network characteristic defined");
		} // if
		
		// check if it is a characteristic name from the ones supported
		if (!characteristic.equals("path.bandwidth.available") && !characteristic.equals("path.delay.roundTrip") ) {
			throw new org.apache.axis.AxisFault ("characteristic: \"" +characteristic+ "\" not supported") ;
		} // if
		
		
		//get source
		try {
			source =(String)((HashMap)((HashMap)((HashMap)request.get ("subject")).get("source")).get("address")).get("name");
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong source") ;
		} // try - catch
		
		if (source == null || source.length() == 0) {
			throw new org.apache.axis.AxisFault ("No source address name specified");
		} // if
		
		// get destination
		try {
			dest =(String)((HashMap)((HashMap)((HashMap)request.get ("subject")).get("destination")).get("address")).get("name");
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong source") ;
		} // try - catch
		
		if (dest == null || dest.length() == 0) {
			throw new org.apache.axis.AxisFault ("No destination address name specified");
		} // if
		
		// get startTime
		try {
			startTime = (String)request.get ("startTime");
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong startTime");
		} // try - catch
		
		if (startTime == null || startTime.length() == 0) {
			throw new org.apache.axis.AxisFault ("Wrong startTime");
		} // if
		
		//getMillisec for mlStartTime
		try {
			Date d = sdf.parse(startTime);
System.out.println ("startTime: "+d);			
			mlStartTime = d.getTime();
System.out.println ("mlStartTime: "+mlStartTime);			
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong time format for startTime ([yyyy-MM-dd'T'HH:mm:ssZ])");
		} // try-catch 
		
		// get endTimes
		try {
			endTime = (String)request.get ("endTime");
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong endTime");
		} // try - catch
		
		if (endTime == null || endTime.length() == 0) {
			throw new org.apache.axis.AxisFault ("Wrong endTime");
		} // if
		
		// get millisec for mlEndTime
		try {
			Date d = sdf.parse (endTime) ;
		
			mlEndTime = d.getTime();

			long current = NTPDate.currentTimeMillis();
			if (mlEndTime > current) {
				mlEndTime = current;
			} // if
			
		} catch (Throwable t) {
			throw new org.apache.axis.AxisFault ("Wrong time format for endTime ([yyyy-MM-dd'T'HH:mm:ssZ])");
		} // try - catch
		
		// check if mlStartTime is greater than mlEndTime
		if (mlStartTime > mlEndTime) {
			throw new org.apache.axis.AxisFault ("startTime greater than endTime");
		} // if
		
		Vector rez ;
		
		if (characteristic.trim().equals ("path.delay.roundTrip")) {
				
			// first find the farm name of the destination hostname
			monPredicate mp = new monPredicate (source, "ABPing", "*", mlStartTime , mlEndTime, new String[]{"name"}, null);
			try {
				rez = _store.select (mp);
			} catch (Exception e) {
	            System.out.println(" Database error ...");
	            // throw database exception
	            throw new org.apache.axis.AxisFault("Database Error");
			} // try - catch 
			
			if (rez == null) {
	            System.out.println(" MLWebService =======> results vector null ... :(");
	            // database exception
	            throw new org.apache.axis.AxisFault("Database Error");
	        } // if rez == null
			
	        if (rez.size() == 0) {
	            System.out.println(" MLWebService =======> No results found ...");
	            throw new org.apache.axis.AxisFault("ABPing hostname not found on this service");
	        } // if   rez.size == 0

			String dstServName = null;
			for (int i=0 ; i < rez.size() ; i++) {
				try {
					lia.Monitor.monitor.eResult r = (lia.Monitor.monitor.eResult) rez.elementAt (i);
					if (((String)(r.param[0])).equals (dest)) {
						dstServName = r.NodeName ;
						break ;
					} // if
				} catch (Throwable t){} // try - catch
			} // for
			
			if (dstServName == null) {
	            throw new org.apache.axis.AxisFault("ABPing hostname not found on this service");				
			} // if 
			
			// create the real predicate
			mp = null ;
			rez = null ;
			mp = new monPredicate (source, "ABPing", dstServName, mlStartTime, mlEndTime, new String[]{"RTT"}, null) ;
			try {
				rez = _store.select (mp);
			} catch (Exception e) {
				throw new org.apache.axis.AxisFault("Database error");
			} // try - catch
			
			if (rez == null) {
				throw new org.apache.axis.AxisFault("Database error");
			} // if
			
			if (rez.size() == 0) {
				throw new org.apache.axis.AxisFault("No results found ...");
			} // if
			
			
			double minVal = Double.MAX_VALUE ;
			double maxVal = Double.MIN_VALUE ;
			double avgVal = 0 ;
			int nrAvgValues = 0;
			
			try {
				for (int i=0 ; i < rez.size() ; i++ ) {
					lia.Monitor.monitor.Result r = (lia.Monitor.monitor.Result) rez.elementAt(i) ;
					if (r instanceof ExtendedResult){
						ExtendedResult er = (ExtendedResult)r ;
					
						if (minVal > er.min) 
							minVal = er.min ;
					
						if (maxVal < er.max)
							maxVal = er.max ;
					
					} else {
						if (minVal > r.param[0])
							minVal = r.param[0];
					
						if (maxVal <r.param[0])
							maxVal = r.param[0];
					} // if - else
				
					avgVal = avgVal + r.param[0];
					nrAvgValues++ ;
				} // for
			
				avgVal = avgVal/nrAvgValues ;
				
				HashMap h4 = new HashMap() ;
				h4.put("min", Double.valueOf(minVal)) ;
				h4.put("max", Double.valueOf(maxVal));
				h4.put ("avg", Double.valueOf(avgVal));
				
				HashMap h3 = new HashMap ();
				h3.put ("resultSet",h4);
				
				HashMap h2 = new HashMap ();
				h2.put ("results", h3);
				
				HashMap h1 = new HashMap ();
				h1.put ("networkMeasurements",h2) ;
				
				return h1;
			} catch (Throwable t) {
				return null;
			} // try - catch
			
		} // characteristic path.delay.roundTrip
		
		
		if (characteristic.trim().equals("path.bandwidth.available")) {
			monPredicate mp = new monPredicate (source, "Pathload", "*", mlStartTime, mlEndTime, new String[]{"AwBandwidth_Low", "AwBandwidth_High"}, null );			
			
			try {
		            rez = _store.select(mp);
		     } catch (Exception e) {
		            System.out.println(" Database error ...");
		            // throw database exception
		            throw new org.apache.axis.AxisFault("Database Error");
		     } // try - catch
			
	        if (rez == null) {
	            System.out.println(" MLWebService =======> results vector null ... :(");
	            // database exception
	            throw new org.apache.axis.AxisFault("Database Error");
	        } // if rez == null

	        if (rez.size() == 0) {
	            System.out.println(" MLWebService =======> No results found ... for predicate "+mp);
	            throw new org.apache.axis.AxisFault("No Results Found");
	        } // if   rez.size == 0
			
			
			double minVal = Double.MAX_VALUE;
			double maxVal = Double.MIN_VALUE;
			double minAvgVal = 0;
			int nrMinValues = 0;
			double maxAvgVal = 0;
			int nrMaxValues = 0;
			double avgVal = 0;
			
			for ( int i=0 ; i < rez.size()   ; i++ ) {
				lia.Monitor.monitor.Result r = (lia.Monitor.monitor.Result) rez.elementAt(i) ;
				
				String destFarm = null;
				int lastIndexOf =-1;
				try {
					lastIndexOf = r.NodeName.lastIndexOf("@");
				} catch (Throwable t){
					lastIndexOf = -1;
				} // try - catch
				
				if (lastIndexOf < 0) {
					destFarm = r.NodeName ;
				} else {
					destFarm = r.NodeName.substring(0,lastIndexOf);
				} // if - else
				
				if (r.NodeName == null || !destFarm.startsWith (dest)){					
					continue;
				} // if
				
				for (int j = 0 ; j < r.param_name.length  ; j++) {
					
					if (r.param_name[j].equals ("AwBandwidth_Low")) {
						minAvgVal = minAvgVal + r.param[j] ;
						nrMinValues++ ;
						if (r.param[j]<minVal) {
							minVal = r.param[j];
						} // if
					} // if
					
					if (r.param_name[j].equals ("AwBandwidth_High")) {
						maxAvgVal = maxAvgVal + r.param[j] ;
						nrMaxValues++ ;
						if (r.param[j]>maxVal) {
							maxVal = r.param[j];
						} // if
					} // if
					
				} // for
			} // for
			
			if (nrMinValues > 0) {
				minAvgVal = minAvgVal/nrMinValues ;
			} // if
			
			if (nrMaxValues > 0) {
				maxAvgVal = maxAvgVal/nrMaxValues ;
			} // if
			 
			avgVal = (minAvgVal + maxAvgVal)/2 ;
			
			HashMap h4 = new HashMap() ;
			h4.put("min", Double.valueOf(minVal)) ;
			h4.put("max", Double.valueOf(maxVal));
			h4.put ("avg", Double.valueOf(avgVal));
			
			HashMap h3 = new HashMap ();
			h3.put ("resultSet",h4);
			
			HashMap h2 = new HashMap ();
			h2.put ("results", h3);
			
			HashMap h1 = new HashMap ();
			h1.put ("networkMeasurements",h2) ;
			
			return h1;
			
		} // if
		
		return null;
    
	} // networkMeasurementSet
	
    public lia.ws.Result[] getValues(java.lang.String in0, java.lang.String in1, java.lang.String in2,
            java.lang.String in3, long in4, long in5) throws java.rmi.RemoteException, org.apache.axis.AxisFault {

        vsCounterValues[2]++;

        lia.Monitor.monitor.monPredicate predicat;
        java.util.Vector rez = null;

        System.out.println(" MLWebService =======> call getValues function");

        if (in3 == null || in3.equals("*")) {
            predicat = new monPredicate(in0, in1, in2, in4, in5, null, null);
        } else {
            String parameter[] = new String[1];
            parameter[0] = in3;
            predicat = new monPredicate(in0, in1, in2, in4, in5, parameter, null);
        } // if - else

        if (_store == null) { throw new org.apache.axis.AxisFault("Database Error"); }

        try {
            rez = _store.select(predicat);
        } catch (Exception e) {
            System.out.println(" MLWebService =======> Database error ...");
            // throw database exception
            throw new org.apache.axis.AxisFault("Database Error");
        } // try - catch

        if (rez == null) {
            System.out.println(" MLWebService =======> results vector null ... :(");
            // database exception
            throw new org.apache.axis.AxisFault("Database Error");
        } // if

        if (rez.size() == 0) {
            System.out.println(" MLWebService =======> No results found ...");
            throw new org.apache.axis.AxisFault("No Results Found");
        }

        // group returned results
        FilterResults fr = new FilterResults();
        Vector rezFinal = fr.filterValues(rez);

        // create the returned result
        lia.ws.Result[] returnResults = new lia.ws.Result[rezFinal.size()];
        for (int i = 0; i < rezFinal.size(); i++) {
            returnResults[i] = (lia.ws.Result) (rezFinal.elementAt(i));
        } // for

        System.out.println(" MLWebService =======> returned " + returnResults.length + " values");

        return returnResults;

    } // getValues

    public lia.ws.WSConf[] getConfiguration(long in0, long in1) throws java.rmi.RemoteException,
            org.apache.axis.AxisFault {

        vsCounterValues[0]++;

        System.out.println(" MLWebService =======> call getConfiguration function");

        java.util.Vector rez;
        lia.ws.WSConf[] result;

        try {
            rez = _store.getConfig(in0, in1);
        } catch (Exception ex) {
            System.out.println(" MLWebService: Database error ");
            throw new org.apache.axis.AxisFault("Database Error");
        }

        if (rez.size() == 0) {
            System.out.println(" MLWebService: no results found in the database ");
            throw new org.apache.axis.AxisFault("No Results Found");
        }

        if (rez.size() > MAX_NR_OF_CONFIGURATIONS) {
            System.out.println(" MLWebService: too many results found in the database");
            throw new org.apache.axis.AxisFault("Too Many Results Found");
        }

        result = new WSConf[rez.size()];
        for (int i = 0; i < rez.size(); i++) {
            result[i] = (lia.ws.WSConf) rez.elementAt(i);
        }

        System.out.println(" MLWebService: found " + result.length + " configurations");

        return result;

    } // getConfiguration

    public lia.ws.WSConf[] getLatestConfiguration(String farm) throws java.rmi.RemoteException,
            org.apache.axis.AxisFault {

        vsCounterValues[1]++;

        System.out.println(" MLWebService =======> call the getLatestConfiguration function");

        if (farm == null) return null;

        Vector rez = new Vector();
        lia.ws.WSConf[] result = null;

        if (_store == null) { throw new org.apache.axis.AxisFault("Database Error"); }

        if (farm.equals("*")) {
            Vector configurationFarms = _store.getConfigurationFarms();

            if (configurationFarms == null) throw new org.apache.axis.AxisFault("No results found ");

            for (int i = 0; i < configurationFarms.size(); i++) {
                String farmName = (String) configurationFarms.elementAt(i);
                Object o = _store.getConfig(farmName);
                if (o != null) {
                    rez.add(o);
                }
            }// for
        } else {
            Object o = _store.getConfig(farm);
            if (o == null) throw new org.apache.axis.AxisFault("No results found ");
            rez.add(o);
        }

        if (rez.size() == 0) {
            System.out.println(" MLWebService =======> No results found in the database");
            throw new org.apache.axis.AxisFault("No Results Found");
        }

        result = new lia.ws.WSConf[rez.size()];
        for (int i = 0; i < rez.size(); i++) {
            result[i] = (lia.ws.WSConf) rez.elementAt(i);
        } // for

        System.out.println(" MLWebService: found latest configurations ");
        return result;

    } // getLatestConfiguration

    public lia.ws.Result[] getLastValues() throws java.rmi.RemoteException {
        vsCounterValues[3]++;

        try {

            ArrayList v = _store.getLatestValues();
            if (v == null) {
                System.out.println(" getLastValues() null .... no values in the store");
                return null;
            }

            ArrayList vr = new ArrayList();

            for (int i = 0; i < v.size(); i++) {
                Object o = v.get(i); 
                if (!(o instanceof lia.Monitor.monitor.Result) && !(o instanceof lia.Monitor.monitor.eResult)) continue;

                lia.Monitor.monitor.Result r = null;
		lia.Monitor.monitor.eResult er = null;
		
		if (o instanceof lia.Monitor.monitor.Result ) {
			r = (lia.Monitor.monitor.Result) o;
	                if (r.param == null) continue;
			
		} else {
			er = (lia.Monitor.monitor.eResult) o;
	                if (er.param == null) continue;
			
		} // if - else	
		

        	    lia.ws.Result rTemp = new lia.ws.Result();

		if (r != null ) {

		    Hashtable h = new Hashtable ();

	            for (int j = 0; j < r.param_name.length; j++)
    	                h.put ( r.param_name[j],  Double.valueOf(r.param[j])) ;

            	    rTemp.setFarmName(r.FarmName);
                    rTemp.setClusterName(r.ClusterName);
	            rTemp.setNodeName(r.NodeName);
    	            rTemp.setTime(r.time);
        	    rTemp.setParam (h);

		} else {
		
			if (er.param[0] instanceof String) {
	            	    rTemp.setFarmName(er.FarmName);
    	            	    rTemp.setClusterName(er.ClusterName);
		            rTemp.setNodeName(er.NodeName);
    	        	    rTemp.setTime(er.time);
			    
			    Hashtable h = new Hashtable ();
			    h.put(er.param_name[0], er.param[0]);
			    			
			    rTemp.setParam(h);
			} // if
				    
		
		} // if - else

                vr.add(rTemp);
            }

            System.err.println("WS.getLastValues() : returning : " + vr.size() + " results");

            return (lia.ws.Result[])vr.toArray(new lia.ws.Result[vr.size()]);
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public lia.ws.Result[] getFilteredLastValues(java.lang.String in0, java.lang.String in1, java.lang.String in2,
            java.lang.String in3) throws java.rmi.RemoteException {
        vsCounterValues[4]++;

        try {
            // java.util.Vector v =
            // lia.Monitor.Store.Cache.getCachedRecentData();
            ArrayList v = _store.getLatestValues();
            System.out.println(" getCachedRecentData  [ " + ((v == null) ? 0 : v.size()) + " ]");

            if (v == null) { return null; }

            java.util.ArrayList vr = new java.util.ArrayList();

            java.lang.String[] sParams = null;

            if (in3 != null && !in3.equals("*") && !in3.equals("%")) {
                sParams = new java.lang.String[1];
                sParams[0] = in3;
            }

            lia.Monitor.monitor.monPredicate p = new lia.Monitor.monitor.monPredicate(in0, in1, in2, -1, -1, sParams,
                    null);

            for (int i = 0; i < v.size(); i++) {
                Object o = v.get(i);
                if (!(o instanceof lia.Monitor.monitor.Result) && !(o instanceof lia.Monitor.monitor.eResult)) {
                    continue;
                }
		lia.Monitor.monitor.Result r=null;
		lia.Monitor.monitor.eResult er = null;
		
		if (o instanceof lia.Monitor.monitor.Result ) {
            	    r = (lia.Monitor.monitor.Result) o;
                    if (r.param == null) continue;
		    
		} else {
		    er = (lia.Monitor.monitor.eResult) o;
                    if (er.param == null) continue;
		    
		} // if - else    

                if ((r!=null && lia.Monitor.DataCache.DataSelect.matchResult(r, p) != null) || (er!=null && lia.Monitor.DataCache.DataSelect.matchResult(er, p) != null)) {
                    lia.ws.Result rTemp = new lia.ws.Result();

		    if (r!=null ) {
                	rTemp.setFarmName(r.FarmName);
                        rTemp.setClusterName(r.ClusterName);
	                rTemp.setNodeName(r.NodeName);
    	                rTemp.setTime(r.time);
		    } else {
			rTemp.setFarmName(er.FarmName);
                        rTemp.setClusterName(er.ClusterName);
	                rTemp.setNodeName(er.NodeName);
    	                rTemp.setTime(er.time);
			
		    }	

		    
		    if (r!=null) {
			
			Hashtable h = new Hashtable ();
                	for (int j = 0; j < r.param.length; j++)
                    	    h.put (r.param_name[j], Double.valueOf(r.param[j]));

                	rTemp.setParam(h);

		    } else {
		    	
			if (er.param[0] instanceof String) {
				Hashtable h = new Hashtable ();
				h.put (er.param_name[0],er.param[0]);
			    	rTemp.setParam(h);
			} // if
		    } // if - else
		    
                    vr.add(rTemp);
                } // if
            } // for

            System.out.println(" Returning  [ " + ((vr == null) ? 0 : vr.size()) + " ]");
            return (lia.ws.Result[])vr.toArray(new lia.ws.Result[vr.size()]);

        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
            return null;
        }
    }

}
