/**
 * Details for an annotation
 */
package lia.web.utils;

import java.awt.Color;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import lia.Monitor.Store.Fast.DB;
import lia.util.StringFactory;
import lia.web.servlets.web.Utils;

/**
 * @author costing
 * @since  2006-06-23
 */
public final class Annotation implements Comparable<Annotation> {
	
	private static final Color DEFAULT_COLOR = ColorFactory.getColor(180, 180, 255);
	
	/**
	 * The unique ID from the database for this annotatio
	 */
	public int id = 0;
	
	/**
	 * Start time of the event, epoch in millis
	 */
	public long from = 0;
	
	/**
	 * End time of the event, epoch in millis
	 */
	public long to   = 0;
	
	/**
	 * Chart groups to which this annotation applies to.
	 * In case this is empty then the annotation will be visible in every rendered chart.
	 */
	public Set<Integer> groups = new TreeSet<Integer>();
	
	/**
	 * The list of services to which this annotation applies to.
	 * In case this is empty then the annotation is considered to be chart-wide and will
	 * be displayed as a bar in the background of the chart.
	 */
	public Set<String> services = new TreeSet<String>();
	
	/**
	 * If bValue is true then this annotation will be displayed as a value range instead of a time range
	 */
	public boolean bValue = false;
	
	/**
	 * The text for the annotation (the reason of the event)
	 */
	public String text = null;
	
	/**
	 * Full description of the problem / solution
	 * This field is private to force the use of {@link #getDescription()} method, that loads the full text from the database when needed to
	 */
	private String description = null;
	
	/**
	 * Color to use when displaying the bars or other non-text elements.
	 */
	public Color color = DEFAULT_COLOR;
	
	/**
	 * Color to use when displaying the text of the annotation, on the image itself or in other places.
	 */
	public Color textColor = DEFAULT_COLOR;
	
	/**
	 * These are internal fields used to automatically arrange the text messages so they are more visible
	 */
	public long leftSpace = 0;
	
	/**
	 * Same.
	 */
	public long rightSpace = 0;
	
	/**
	 * Void constructor. To be used for creating new annotations and saving them in the database. 
	 */
	public Annotation(){
		// nothing to do
	}
	
	/**
	 * When a single (known) annotation is needed you can use this constructor. It will query the database
	 * for this entry. If the entry is not found then the "id" field will have the default 0 value after this call.
	 * Use this constructor when you want for example to edit a given annotation.
	 * 
	 * @param _id the requested annotation
	 */
	public Annotation(final int _id){
		final DB db = new DB();
		
		db.setReadOnly(true);
		
		if (db.query("SELECT * FROM annotations WHERE a_id="+_id+";") && db.moveNext()){
			init(db);
		}
	}
	
	/**
	 * When you already have executed the query you can pass the DB object to this constructor.
	 * This constructor is called from {@link Annotations} when it build the list of annotation
	 * for a requested time interval.
	 * 
	 * @param db a {@link DB} object
	 */
	public Annotation(final DB db){
		init(db);
	}
	
	/**
	 * Internal method to fill all the fields with database values.
	 * 
	 * @param db a DB object
	 */
	private void init(final DB db){
		id   = db.geti("a_id");
		from = db.getl("a_from")*1000;
		to   = db.getl("a_to")*1000;
		text = StringFactory.get(db.gets("a_text"));
		description = db.getns("a_fulldesc");
		
		color = ServletExtension.getColor(db.gets("a_color"), color);
		textColor = ServletExtension.getColor(db.gets("a_textcolor"), color); 
		
		groups = decodeGroups(db.gets("a_groups"));
		services = decode(db.gets("a_services"));
		
		bValue = db.geti("a_value", 0)!=0;
	}
	
	private static final Object syncObject = new Object();
	
	/**
	 * Call this method to save the new annotation or update the existing one in the database.
	 */
	public void updateDatabase(){
		final DB db = new DB();
		
		if (id==0){
			synchronized (syncObject){
				db.query("SELECT max(a_id) FROM annotations;");
				
				id = db.geti(1) + 1;
			
				db.syncUpdateQuery("INSERT INTO annotations (a_id, a_from, a_to, a_text, a_fulldesc, a_color, a_textcolor, a_groups, a_services, a_value) VALUES ("+
					id+","+
					from/1000+","+
					to/1000+","+
					"'"+Formatare.mySQLEscape(text)+"',"+
					"'"+Formatare.mySQLEscape(description)+"',"+
					"'#"+Utils.toHex(color)+"',"+
					"'#"+Utils.toHex(textColor)+"',"+
					(groups.size()==0 ? "null" : "'"+encode(groups)+"'")+","+
					(services.size()==0 ? "null" : "'"+Formatare.mySQLEscape(encode(services))+"'")+","+
					(bValue ? "1" : "0")+
					");"
				);
			}
		}
		else{
			if (description==null)
				getDescription();
			
			db.syncUpdateQuery("UPDATE annotations SET a_from="+from/1000+", a_to="+to/1000+", a_text='"+Formatare.mySQLEscape(text)+"',"+
					"a_fulldesc='"+Formatare.mySQLEscape(description)+"',"+
					"a_color='#"+Utils.toHex(color)+"',"+
					"a_textcolor='#"+Utils.toHex(textColor)+"',"+
					"a_groups="+(groups.size()==0 ? "null" : "'"+encode(groups)+"'")+","+
					"a_services="+(services.size()==0 ? "null" : "'"+Formatare.mySQLEscape(encode(services))+"'")+","+
					"a_value="+(bValue ? "1" : "0")+" "+
					"WHERE a_id="+id+";"
			);
		}
	}
	
	/**
	 * Delete this annotation from the database
	 */
	public void deleteDatabaseEntry(){
		final DB db = new DB();
		
		if (id==0)
			db.query("DELETE FROM annotations WHERE id="+id+";");
	}
	
	/**
	 * Split a String into a Set if Integers (the groups)
	 * 
	 * @param groups
	 * @return a Set of Integers
	 */
	public static Set<Integer> decodeGroups(final String groups){
		final TreeSet<Integer> ts = new TreeSet<Integer>();
		
		final Iterator<String> it = decode(groups).iterator();
		
		while (it.hasNext()){
			try{
				ts.add(Integer.valueOf(it.next()));
			}
			catch (Exception e){
				// ignore
			}
		}
		
		return ts;
	}
	
	/**
	 * Create a database representation of an array
	 * 
	 * @param s
	 * @return a String representation of an array of values (PG-style)
	 */
	public static String encode(final Set<?> s){
		final Iterator<?> it = s.iterator();
		
		final StringBuilder sb = new StringBuilder("{");
		
		while (it.hasNext()){
			if (sb.length()>1)
				sb.append(',');
			
			sb.append(it.next().toString());
		}

		return sb.append("}").toString();
	}
	
	/**
	 * Split the given String into a Set of Strings
	 * 
	 * @param s
	 * @return a Set of Strings, the tokens from the original String
	 */
	public static Set<String>  decode(final String s){
		final TreeSet<String> ts = new TreeSet<String>();
		
		final StringTokenizer st = new StringTokenizer(s, "{},");
		
		while (st.hasMoreTokens()){
			try{
				ts.add(StringFactory.get(st.nextToken().trim()));
			}
			catch (Exception e){
				// ignore
			}
		}
		
		return ts;
	}
	
	@Override
	public String toString(){
		final StringBuilder sb = new StringBuilder();
		
		sb.append(id).append(". ").
		   append(new Date(from)).append(" - ").
		   append(new Date(to)).append(" : ").
		   append(text).append(" : ").
		   append(encode(groups)).
		   	append(" (").append(Utils.toHex(color)).append(", ").append(Utils.toHex(textColor)).append(") - ").
		   append(encode(services)).
		   append(" - ").append(bValue);
		
		return sb.toString();
	}
	
	@Override
	public int compareTo(final Annotation a){
		if (id>0 && id==a.id)
			return 0;
		
		if (from<a.from) return -1;
		if (from>a.from) return 1;

		if (id>0 && a.id>0){
			if (id<a.id) return -1;
			
			return 1;
		}
		
		if (to<a.to) return -1;
		if (to>a.to) return 1;
		
		if (text==null){
			if (a.text!=null)
				return -1;
		}
		else{
			if (a.text!=null)
				return text.compareTo(a.text);
		}
		
		if (bValue ^ a.bValue)
			return bValue ? 1 : -1;
		
		return 0;
	}
	
	@Override
	public boolean equals(final Object o){
		return o!=null ? compareTo((Annotation)o)==0 : false;
	}
	
	@Override
	public int hashCode(){
		return (int) ((((from*31+to)*31+id)*31+(text!=null ? text.hashCode() : 11)))*31+(bValue ? 1 : 0);
	}

	/**
	 * Get the full description from the database
	 * 
	 * @return the full description of the problem/solution
	 */
	public String getDescription(){
		if (description==null && id>0){
			final DB db = new DB();
			
			db.setReadOnly(true);
			
			db.query("SELECT a_fulldesc FROM annotations WHERE a_id="+id+";");
			
			if (db.moveNext()){
				description = db.getns(1);
				
				if (description!=null)
					StringFactory.get(description);
			}
		}
		
		return description;
	}
	
	/**
	 * The setter for the description field
	 * 
	 * @param s
	 */
	public void setDescription(final String s){
		description = s;
	}
}
