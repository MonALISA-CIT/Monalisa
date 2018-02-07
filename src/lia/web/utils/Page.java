package lia.web.utils;

import java.io.OutputStream;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Set;

import lazyj.page.BasePage;
import lazyj.page.tags.JS;
import lia.Monitor.Store.Fast.DB;

/**
 * Wrapper around an HTML template parser
 * 
 * @author costing
 * @since forever
 */
public class Page extends BasePage {

	static{
		// compatibility with old templates that had this "js2" tag to do exactly the same thing as "js" in lazyj
		BasePage.registerExactTag("js2", new JS());
	}
	
	/**
	 * Create an empty container to which you can add anything
	 */
	public Page(){
		super();
	}

	/**
	 * Load the template from a file
	 * 
	 * @param _sFile
	 */
	public Page(final String _sFile){
		super(_sFile);
	}

	/**
	 * Load the template from a file but also indicate the output stream where the object will {@link #write()} itself.
	 * 
	 * @param _os
	 * @param _sFile
	 */
	public Page(final OutputStream _os, final String _sFile){
		super(_os, _sFile);
	}
	
	/**
	 * Legacy parameter that we cannot get rid of without major trouble.
	 * 
	 * @param _os
	 * @param _sFile
	 * @param _bCompressed not used any more
	 * @deprecated
	 */
	@Deprecated
	public Page(final OutputStream _os, final String _sFile, final boolean _bCompressed){
		super(_os, _sFile);
	}

	/**
	 * @param _os
	 * @param _sFile
	 * @param _bCompressed
	 * @param _bCached
	 */
	public Page(final OutputStream _os, final String _sFile, final boolean _bCompressed, final boolean _bCached){
		super(_os, _sFile, _bCached);
	}
	
	/**
	 * Load a template optionally indicating whether or not to cache the contents of the template file.
	 * 
	 * @param _sFile
	 * @param _bCached
	 */
	public Page(final String _sFile, final boolean _bCached){
		super(null, _sFile, _bCached);
	}

	/**
	 * shortcut for simple types
	 * 
	 * @param sTag
	 * @param i
	 */
	public final void modify(final String sTag, final int i){
		modify(sTag, String.valueOf(i));
	}

	/**
	 * shortcut for simple types
	 * 
	 * @param sTag
	 * @param i
	 */
	public final void modify(final String sTag, final long i){
		modify(sTag, String.valueOf(i));
	}

	/**
	 * shortcut for insert
	 * 
	 * @param sTag
	 * @param sText
	 */
	public final void insert(final String sTag, final String sText){
		append(sTag, sText, true);
	}

	/**
	 * shortcut for insert
	 * 
	 * @param sTag
	 * @param p
	 */
	public final void insert(final String sTag, final Page p){
		append(sTag, p, true);
	}

	/**
	 * shortcut for making a tag "checked" if the parameter is true
	 * 
	 * @param sTag
	 * @param bChecked
	 */
	public final void check(final String sTag, final boolean bChecked){
		modify(sTag, bChecked?"checked":"");
	}

	/**
	 * Set all the fields that have the same name with the columns in the database with the values
	 * from the current rows in the database
	 * 
	 * @param db
	 * @see lazyj.page.BasePage#fillFromDB(lazyj.DBFunctions)
	 */
	public void fillFromDB(final DB db){
		fillFromDB(db, true);
	}

	/**
	 * Set all the fields that have the same name with the columns in the database with the values
	 * from the current rows in the database.
	 * 
	 * @param db
	 * @param bLegacy if <code>true</code> then all fields that have the same name in both the template and the 
	 * 	database meta are set, if <code>false</code> then only those that have the "db" option attached are considered.
	 */
	public void fillFromDB(final DB db, final boolean bLegacy){
		if (db==null)
			return;
		
		final ResultSetMetaData meta = db.getMetaData();
		
		if (meta==null)
			return;
		
		final Set<String> tagsSet;
		
		if (bLegacy){
			tagsSet = getTagsSet();
		}
		else{
			tagsSet = getDBTags();
		}
		
		if (tagsSet==null)
			return;
		
		try {
			for (int i=meta.getColumnCount(); i>0; i--){
				final String sKey = meta.getColumnLabel(i);
				
				if (tagsSet.contains(sKey))
					modify(meta.getColumnLabel(i), db.gets(i));
			}
		} catch (SQLException e) {
			// ignore
		}
	}
	
}
