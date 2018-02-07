/*
 * Created on Sep 6, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.VRVS3D.Tabl;

import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TabPanBase;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TableSorter;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;

/**
 * @author mluc
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class TabPan extends TabPanBase {
	
	public TabPan () {
		super();
		Vector vColumns=new Vector();
		ColumnProperties colP;

		colP = new ColumnProperties( "Reflector", "text", "<html><b>Reflector</b>", 110, 110, myren);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "Hostname", "text", "<html><b>Hostname</b>", 130, 120);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "Load", "number", "<html><b>Load</b><br>mean", 60, 50);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "Video", "number", "<html><b>Video</b><br>Clients ", 70, 70);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "Audio", "number", "<html><b>Audio</b><br>Clients ", 70, 70);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "MLVer", "version", "<html><b>MonaLisa<br>Version</b>", 90, 90);
		colP.setFixed(true);
		vColumns.add( colP);
        colP = new ColumnProperties("JavaVer", "version", "<html><b>Java VM<br>Version</b>", 85, 85);
        colP.setFixed(true);
        vColumns.add(colP);
		colP = new ColumnProperties( "ReflVer", "ReflVersion", "<html><b>Reflector<br>Version</b>", 90, 90);
		colP.setFixed(true);
		vColumns.add( colP);
		colP = new ColumnProperties( "MLUptime", "uptime", "<html><b>ML UpTime</b>", 90, 90);
		vColumns.add( colP);
		colP = new ColumnProperties( "Group", "text", "<html><b>Group</b>", 70, 70);
		vColumns.add( colP);
		
		ginit( new rcTableModel(vColumns));
	}
	
    public static final String getJavaVersion(ExtendedSiteInfoEntry esie) {
        if(esie != null && esie.JVM_VERSION != null) {
            final String[] splitTks = esie.JVM_VERSION.split("(\\s)+");
            return splitTks[splitTks.length - 1];
        }
        return "N/A";
    }

    public static final String getJavaVersion(rcNode n) {
        if (n != null && n.client != null && n.client.esie != null) {
            return getJavaVersion(n.client.esie);
        }
        return "N/A";
    }
    
    class rcTableModel extends MyTableModel {
    	public rcTableModel(Vector vColumns) {
    		super(vColumns);
    	}
    	
		public int getRowCount() {
			if ( nodes== null ) return 0;
			return nodes.size();
		}
		
		   /**
	     * gets the value that will be rendered on the table from the internal structure
	     * based on two indecs: row and col that hopefully uniquely identify an element
	     * in the structure.<br>
	     * Ougth to be rewrited in derived classes.
	     * @param row
	     * @param col
	     * @return the object value
	     */
		public Object getValueAt(int row, int col) {
			if ( nodes == null ) return null;
			if ( nodes.size() == 0 ) return null;
			if ( row > (nodes.size()-1 ) ) return null;
			if ( row < 0 )
				return getColumnType(col);
			rcNode n =  null;
			try {
			    n = vnodes.elementAt(row) ;
			} catch(Exception ex) {
			    n = null;
			}
			if ( n == null ) return null;
			//return node if column is negative
			if ( col==-1 )
				return n;
			if ( col <0  )
				return null;
			
			Object obj;
			String codename = getColumnCodeName(col);
			if ( codename.equals("Reflector") ) return n==null?null:n.UnitName;
			else if ( codename.equals("Hostname") ) return n==null || n.client==null?"???":n.client.hostName;
			else if ( codename.equals("Load") ) {
				obj = n.haux.get("Load");
				if ( obj == null )
					return  "???";
				return ""+((DoubleContainer)obj).getValue();
			} else if ( codename.equals("Video") ) {
				obj = n.haux.get("Video");
				if ( obj == null )
					return  "0";
				return ""+((int)((DoubleContainer)obj).getValue());
			} else if ( codename.equals("Audio") ) {
				obj = n.haux.get("Audio");
				if ( obj == null )
					return  "0";
				return ""+((int)((DoubleContainer)obj).getValue());
			} else if ( codename.equals("MLVer") ) return n==null || n.client==null?"???":n.client.mlVersion;
            if ( /* col == 2 */codename.equals("JavaVer"))
                return getJavaVersion(n);
			else if ( codename.equals("ReflVer") ) { 
				String ver = (String) n.haux.get("ReflVersion");
				return (ver == null || ver.length() == 0 ? "N/A" : ver);
			}else if ( codename.equals("MLUptime") )
				return n.client.uptime;
			else if ( codename.equals("Group") ) 
				return (n!=null && n.client!=null && n.client.mle !=null) ? n.client.mle.Group:"???";
			return null;
		}
		
		public Object getTotalValueAt(int col) {
			String defVal="-";
			if ( col <0  )
				return defVal;
			String codename = getColumnCodeName(col);
			int ncount = vnodes.size();
			if ( ncount==0 )
				return defVal;
			if ( codename.equals("Reflector") ) return "TOTALS";
			if ( codename.equals("Hostname") ) return ""+ncount;
			if ( codename.equals("ReflVer") ) return defVal;
			if ( codename.equals("MLVer") ) return defVal;
			if ( codename.equals("Group") ) {
				//count groups
				try {
					HashSet<String> uniqueSet = new HashSet<String>();
					for ( int i=0; i<ncount; i++) {
						rcNode n = vnodes.get(i);
						String groups;
						if (n!=null && n.client!=null && n.client.mle !=null && (groups=n.client.mle.Group)!=null ) {
			                StringTokenizer stk = new StringTokenizer(groups, ",");
			                while(stk.hasMoreTokens()) {
			                    String group = stk.nextToken();
			                    uniqueSet.add(group);
			                }
				        }
					}
					return ""+uniqueSet.size();
				} catch(Exception ex) {
					//error counting, should keep previous value
					return null;
				}
			}
			if ( codename.equals("MLUptime") ) {
				//return total up time for all services in table
				try {
					int years, days, hours, mins, secs;
					years = days = hours = mins = secs = 0;
					boolean bValue = false;
					for ( int i=0; i<ncount; i++) {
						rcNode n = (rcNode)vnodes.get(i);
						String uptime;
						if (n!=null && n.client!=null && (uptime=n.client.uptime) !=null ) {
				    		int hour=0, min=0, sec=0, day=0;
					        int nP1, nP1ant=uptime.length();
					        nP1 = uptime.lastIndexOf(':', nP1ant);
					        if ( nP1!=-1 ) {
						        try { 
						        	sec = (int)TableSorter.getNumber(uptime.substring(nP1+1)); 
						        } catch ( NumberFormatException nfex ) { sec = 0; };
						        nP1ant = nP1-1;
						        nP1 = uptime.lastIndexOf(':', nP1ant);
						        if ( nP1!=-1 ) {
							        try { 
							        	min = (int)TableSorter.getNumber(uptime.substring(nP1+1, nP1ant+1));//Integer.parseInt(s1.substring(nP1+1, nP1ant+1)); 
							        } catch ( NumberFormatException nfex ) { min = 0; };
							        nP1ant = nP1-1;
							        nP1 = uptime.lastIndexOf(' ', nP1ant);
							        try { 
							        	hour = (int)TableSorter.getNumber(uptime.substring(nP1+1, nP1ant+1));//Integer.parseInt(s1.substring(nP1+1, nP1ant+1)); 
							        	nP1 = uptime.indexOf(' ');
								        if ( nP1!=-1 )
								            day = (int)TableSorter.getNumber(uptime.substring(0, nP1));//Integer.parseInt(s1.substring( 0, nP1));
							        } catch ( NumberFormatException nfex ) { };
						        };
					        };
					        if ( sec+min+hour+day>0 ) {
						        //add current gathered days, hours, minutes, seconds
						        secs += sec;
						        mins += (secs / 60); secs %= 60;
						        mins += min;
						        hours += (mins / 60); mins %= 60;
						        hours += hour;
						        days += (hours / 24); hours %= 24;
						        days += day;
						        
						        years += (days / 365); days %= 365;
						        bValue = true;
					        }
				        }//end if n!=null
					}//end for
					if ( !bValue )
						return defVal;
			        //compute mean value
					days += years%ncount*365; years /= ncount;
					hours += days%ncount*24; days /= ncount;
					mins += hours%ncount*60; hours /= ncount;
					secs += mins%ncount*60; mins /= ncount;
					secs /= ncount;
					return (years>0?(years==1?"one year ":years+" years "):"")+(days>0?days+" days ":"")+hours+"h"+mins+"m"+secs+"s";
				} catch(Exception ex) {
					//error counting, should keep previous value
					return null;
				}
			};
			if ( codename.equals("Video") || codename.equals("Audio") 
					|| codename.equals("Load") ) {
				try {
					Object obj;
					double total = 0;
					double val;
					boolean bVal = false;
					for ( int i=0; i<ncount; i++) {
						rcNode n = (rcNode)vnodes.get(i);
						if ( n==null || n.client==null || (obj=n.haux.get(codename))==null ) 
							continue;
						try {
							val = ((DoubleContainer)obj).getValue();
							if ( codename.equals("Video") || codename.equals("Audio") )
								val = (int)val;
							bVal = true;
						} catch(Exception ex) { val =0; }
						total += val;
					}
					if ( bVal )
						if ( codename.equals("Load") ) {
							total /= ncount;
							total = ((double)((int)(total*100)))/100.0;
							return ""+total;
						} else
							return ""+(int)total;
					else return defVal;
				} catch(Exception ex) {
					//error counting, should keep previous value
					return null;
				}
			}
			return defVal;
		}
		
		public String getTotalsToolTip(int col) {
			String codename = getColumnCodeName(col);
			if ( codename.equals("Reflector") ) return "Reflector Name";
			if ( codename.equals("Hostname") ) return "Total number of reflectors visible in Table";
			if ( codename.equals("ReflVer") ) return null;
			if ( codename.equals("MLVer") ) return null;
			if ( codename.equals("Group") ) return "Total number of groups visible in Table";
			if ( codename.equals("MLUptime") ) return "Mean uptime for all visible services in Table";
	        if ( codename.equals("Video") ) return "Total video clients for all reflectors visible in Table"; 
	        if ( codename.equals("Audio") ) return "Total audio clients for all reflectors visible in Table"; 
	        if ( codename.equals("Load") ) return "Mean value for Load on MonALISA Service for all reflectors visible in Table";
			return null;
		}
		
	}

}
