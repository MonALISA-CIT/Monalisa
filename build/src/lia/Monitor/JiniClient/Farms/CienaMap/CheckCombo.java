package lia.Monitor.JiniClient.Farms.CienaMap;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.net.URL;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class CheckCombo implements PopupMenuListener {
	
	private String[] args = new String[0];
	public final TreeMap stores = new TreeMap();
	private JComboBox combo = null;
	
	private final CienaMapPan owner;
	
	int size = 50;
	
	private boolean enabled = false;
	
	public CheckCombo(CienaMapPan owner, int size) {
		this.owner=owner;
		this.size = size;
	}
	
	public boolean getValueFor(String name) {
		if (name == null || !stores.containsKey(name)) return false;
		CheckComboStore s = (CheckComboStore)stores.get(name);
		return s.state.booleanValue();
	}
	
	public synchronized void setContent(String[] args) {
		if (args == null) 
			this.args = new String[0];
		else {
			this.args = args;
		}
		// check if we need to add some combos
		if (!stores.containsKey("All")) {
			CheckComboStore s = new CheckComboStore("All", Boolean.valueOf(false));
			stores.put("All", s);
			if (combo != null)
				combo.addItem(s);
		}
		if (!stores.containsKey("None")) {
			CheckComboStore s = new CheckComboStore("None", Boolean.valueOf(false));
			stores.put("None", s);
			if (combo != null)
				combo.addItem(s);
		}
		for (int i=0; i<this.args.length; i++) {
			final String a = this.args[i];
			if (!stores.containsKey(a)) {
				// add it..
				CheckComboStore s = new CheckComboStore(a, Boolean.valueOf(false));
				stores.put(a, s);
				if (combo != null)
					combo.addItem(s);
			}
		}
		// check for the list of removable items...
		final Vector v = new Vector();
		for (Iterator en = stores.keySet().iterator(); en.hasNext(); ) {
			String k = (String)en.next();
			if (k.equals("All") || k.equals("None")) continue;
			boolean found = false;
			for (int i=0; i<this.args.length; i++) {
				if (this.args[i].equals(k)) { found = true; break; }
			}
			if (!found) v.add(k);
		}
		for (int i=0; i<v.size(); i++) {
			CheckComboStore s = (CheckComboStore)stores.remove(v.get(i));
			if (combo != null)
				combo.removeItem(s);
		}
		if (combo != null) {
			combo.repaint();
			combo.revalidate();
			combo.setEnabled(enabled && combo.getItemCount() > 2);
		}
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		combo.setEnabled(enabled && combo.getItemCount() > 2);
	}
	
	public synchronized JPanel getContent() {
		CheckComboStore[] s = new CheckComboStore[stores.size()];
		int i=0;
		for (Iterator en = stores.keySet().iterator(); en.hasNext() && i<s.length; ) {
			s[i] = (CheckComboStore)stores.get(en.next()); 
			i++;
		}
		combo = new JComboBox(s);
		combo.setEnabled(false);
		combo.setRenderer(new CheckComboRenderer());
		combo.addPopupMenuListener(this);
		combo.setFont(new Font("Arial", Font.BOLD, 10));
		JPanel panel = new JPanel();
		panel.add(combo);
		return panel;
	}

	boolean canceled = false;
	
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		canceled = false;
	}

	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
		if (canceled) return;
		JComboBox cb = (JComboBox)e.getSource();
		CheckComboStore store = (CheckComboStore)cb.getSelectedItem();
//		System.out.println(store.id);
		CheckComboRenderer ccr = (CheckComboRenderer)cb.getRenderer();
		ccr.checkBox.setSelected((store.state = Boolean.valueOf(!store.state.booleanValue())).booleanValue());
		if (store.id.equals("All")) {
			for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
				String n = (String)it.next();
				if (n.equals("All")) continue;
				if (n.equals("None")) {
					CheckComboStore s = (CheckComboStore)stores.get(n);
					s.state = Boolean.valueOf(!store.state.booleanValue());
					continue;
				}
				CheckComboStore s = (CheckComboStore)stores.get(n);
				s.state = Boolean.valueOf(store.state.booleanValue());
			}
		} else
		if (store.id.equals("None")) {
			for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
				String n = (String)it.next();
				if (n.equals("None")) continue;
				if (n.equals("All")) {
					CheckComboStore s = (CheckComboStore)stores.get(n);
					s.state = Boolean.valueOf(!store.state.booleanValue());
					continue;
				}
				CheckComboStore s = (CheckComboStore)stores.get(n);
				s.state = Boolean.valueOf(!store.state.booleanValue());
			}
		} else {
			int nrSelected = 0;
			for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
				String n = (String)it.next();
				if (n.equals("All") || n.equals("None")) continue;
				CheckComboStore s = (CheckComboStore)stores.get(n);
				if (s.state.booleanValue()) nrSelected ++;
			}
			if (nrSelected >= combo.getItemCount()-2) { // all
				for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
					String n = (String)it.next();
					if (n.equals("All")) {
						CheckComboStore s = (CheckComboStore)stores.get(n);
						s.state = Boolean.TRUE;
					}else if (n.equals("None")) {
						CheckComboStore s = (CheckComboStore)stores.get(n);
						s.state = Boolean.FALSE;
					}
				}				
			} else if (nrSelected == 0) { // none
				for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
					String n = (String)it.next();
					if (n.equals("None")) {
						CheckComboStore s = (CheckComboStore)stores.get(n);
						s.state = Boolean.TRUE;
					}else if (n.equals("ALL")) {
						CheckComboStore s = (CheckComboStore)stores.get(n);
						s.state = Boolean.FALSE;
					}
				}				
			} else {
				for (Iterator it = stores.keySet().iterator(); it.hasNext(); ) {
					String n = (String)it.next();
					if (n.equals("None") || n.equals("All")) {
						CheckComboStore s = (CheckComboStore)stores.get(n);
						s.state = Boolean.FALSE;
					}
				}
			}
		}
		if (owner != null)
			owner.comboChanged(this);
	}

	public void popupMenuCanceled(PopupMenuEvent e) {
		canceled = true;
	}
	
	/** adapted from comment section of ListCellRenderer api */
	class CheckComboRenderer implements ListCellRenderer {
		JCheckBox checkBox;
		Font f1 = new Font("Arial", Font.PLAIN, 10);
		Font f2 = new Font("Arial", Font.BOLD, 10);
		public CheckComboRenderer() {
			Icon checkBoxIcon = new CheckBoxIcon();
			checkBox = new JCheckBox(checkBoxIcon); 
			Dimension d= new Dimension(size, 13);
			checkBox.setPreferredSize(d);
			checkBox.setMaximumSize(d);
			checkBox.setMinimumSize(d);
		}
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			CheckComboStore store = (CheckComboStore)value;
			if (store == null) return checkBox;
			checkBox.setText(store.id);
			if (store.id.equals("All") || store.id.equals("None"))
				checkBox.setFont(f2);
			else
				checkBox.setFont(f1);
			checkBox.setToolTipText(store.id);
			checkBox.setSelected(((Boolean)store.state).booleanValue());
			checkBox.setBackground(isSelected ? Color.red : Color.white);
			checkBox.setForeground(isSelected ? Color.white : Color.black);
			return checkBox;
		}
		
		private class CheckBoxIcon implements Icon {
		    private ImageIcon checkedIcon;
		    private ImageIcon uncheckedIcon; 
		    
		    public CheckBoxIcon() {
		    	try {
		    		URL r = getClass().getResource("/lia/images/checked.gif");
		    		checkedIcon = new ImageIcon(r);
		    	} catch (Exception e) { }
		    	try {
		    		URL r = getClass().getResource("/lia/images/unchecked.gif");
		    		uncheckedIcon = new ImageIcon(r);
		    	} catch (Exception e) { }
		    }
		    
		    public void paintIcon(Component component, Graphics g, int x, int y) {
		      AbstractButton abstractButton = (AbstractButton) component;
		      ButtonModel buttonModel = abstractButton.getModel();
		      g.translate(x, y);
		      ImageIcon imageIcon = buttonModel.isSelected() ? checkedIcon
		          : uncheckedIcon;
		      Image image = imageIcon.getImage();
		      g.drawImage(image, 3, 3, component);
		      g.translate(-x, -y);
		    }

		    public int getIconWidth() {
		      return 20;
		    }

		    public int getIconHeight() {
		      return 20;
		    }
		  }
	}

	class CheckComboStore {
		String id;
		Boolean state;
		public CheckComboStore(String id, Boolean state) {
			this.id = id;
			this.state = state;
		}
	}
}

