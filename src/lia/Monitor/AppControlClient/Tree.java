package lia.Monitor.AppControlClient;

import java.util.Vector;

public class Tree {

	public Vector children = null;

	public Tree parent = null;

	public String name = null;

	public Vector attNames = null;

	public Vector attValues = null;

	public String text = null;

	
	public Tree(Tree parent, String name) {

		this.parent = parent;
		this.name = name;
	}

	
	public Tree setChild(String name) {

		if (children == null)
			children = new Vector();
		Tree tree = new Tree(this, name);
		children.add(tree);
		return tree;
	}

	
	public void setAttribute(String attName, String attValue) {

		if (attNames == null) {
			attNames = new Vector();
			attValues = new Vector();
		}
		attNames.add(attName);
		attValues.add(attValue);
	}

	
	public String getAttribute(String attName) {

		if (attNames == null)
			return null;
		for (int i = 0; i < attNames.size(); i++) {
			String t = (String) attNames.get(i);
			if (t.equals(attName))
				return ((String) attValues.get(i));
		}
		return null;
	}
	
	public String toString () {
		String txt = "" ;
		for (int i=0;attNames!=null && i<attNames.size();i++) {
			if (((String)(attNames.elementAt (i))).equals("name") || ((String)(attNames.elementAt (i))).equals("app") ) {
				txt+="   "+lia.app.AppUtils.dec((String)(attValues.get(i))) ;
				break;
			}
		}
			
		return txt ;
	}//toString 

} // end of class Tree
