/**
 * Common annotations functions.
 * Currently only database initialization calls and time interval requests are implemented.
 */
package lia.web.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lia.Monitor.Store.Fast.DB;

/**
 * @author costing
 * @since 2006-06-23
 */
public class Annotations {

	private static void initDatabase(){
		final DB db = new DB();
		
		if (db.syncUpdateQuery("create table annotations(a_id serial primary key, a_from int, a_to int, a_groups int[], a_text text, a_color text, a_textcolor text, a_services text[], a_value int, a_fulldesc text);", true)){
			db.syncUpdateQuery("create index annotations_from_idx on annotations(a_from);", true);
			db.syncUpdateQuery("create index annotations_to_idx on annotations(a_to);", true);
		}
		else{
			db.syncUpdateQuery("alter table annotations add column a_value int;", true);
			db.syncUpdateQuery("alter table annotations add column a_fulldesc text;", true);
		}
		
		db.syncUpdateQuery("create table annotation_groups (ag_id serial primary key, ag_name text not null);", true);
	}
	
	static{
		initDatabase();
	}
	
	/**
	 * Get the list of annotations for a time interval and a set of chart groups.
	 * 
	 * @param lStartTime the starting epoch time 
	 * @param lEndTime the ending epoch time
	 * @param groups the set of groups to which the current chart belongs to. Can be
	 * 			empty if you want to see all the events on one chart.
	 * @return a list of annotations that apply to this chart
	 */
	public static List<Annotation> getAnnotations(final long lStartTime, final long lEndTime, final Set<Integer> groups){
		return getAnnotations(lStartTime, lEndTime, groups, 0, 0, false);
	}
	
	/**
	 * Get the list of annotations for a time interval and a set of chart groups.
	 * 
	 * @param lStartTime the starting epoch time 
	 * @param lEndTime the ending epoch time
	 * @param groups the set of groups to which the current chart belongs to. Can be
	 * 			empty if you want to see all the events on one chart.
	 * @param limit how many last entries to return
	 * @param offset how many last entries to skip
	 * @param bPreloadDescr true=load descriptions from the beginning, false=load them only by demand
	 * @return a list of annotations that apply to this chart
	 */
	public static List<Annotation> getAnnotations(final long lStartTime, final long lEndTime, final Set<Integer> groups, final int limit, final int offset, final boolean bPreloadDescr){
		final ArrayList<Annotation> al = new ArrayList<Annotation>();
		
		final DB db = new DB();
		
		final long lStart = lStartTime / 1000;
		final long lEnd   = lEndTime / 1000;
		
		String sQuery = "SELECT ";
		
		sQuery+=bPreloadDescr ? "*" : "a_id,a_from,a_to,a_groups,a_text,a_color,a_textcolor,a_services,a_value";
		
		sQuery+=" FROM annotations WHERE "+
				"(a_from>="+lStart+" and a_from<="+lEnd+") or "+
				"(a_to>="+lStart+" and a_to<="+lEnd+") or "+
				"(a_from<="+lStart+" and a_to>="+lEnd+") or "+
				"(a_value=1) ";
		
		if (limit > 0){
			sQuery = "SELECT * FROM ("+sQuery+" ORDER BY a_to DESC, a_id DESC, a_from DESC LIMIT "+limit+" OFFSET "+offset+") AS x ";
		}
		
		sQuery += "ORDER BY a_from, a_id, a_to;";
		
		db.setReadOnly(true);
		
		db.query(sQuery);
		
		while (db.moveNext()){
			final Annotation a = new Annotation(db);
			
			// annotations with no specific group will be displayed all over the site
			if (a.groups.size()==0){
				al.add(a);
				continue;
			}
			
			// if the requested list of groups is empty do not filter, the chart wants to see all the annotations
			if (groups.size()>0)
				a.groups.retainAll(groups);
			
			if (a.groups.size()>0)
				al.add(a);
		}
		
		return al;
	}
	
}
