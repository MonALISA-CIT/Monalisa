package lia.Monitor.JiniClient.VRVS3D;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.ListModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import lia.Monitor.JiniClient.VRVS3D.Gmap.GraphPan;

/**
 * A window used to search for a particular user in a JList....
 */
public class SearchUserLayer extends JPanel implements DocumentListener, FocusListener {

	private JTextField entry;
	private JLabel jLabel1;
	private JScrollPane jScrollPane1;
	private JLabel status;
	private JList userArea;
	private DefaultListModel model;
	
	private List users = new ArrayList();

	final static Color  HILIT_COLOR = Color.LIGHT_GRAY;
	final static Color  ERROR_COLOR = Color.PINK;
	final static String CANCEL_ACTION = "cancel-search";

	final Color entryBg;

	public SearchUserLayer() {
		
		initComponents();
		
		addFocusListener(this);

		entryBg = entry.getBackground();
		entry.getDocument().addDocumentListener(this);

		InputMap im = entry.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = entry.getActionMap();
		im.put(KeyStroke.getKeyStroke("ESCAPE"), CANCEL_ACTION);
		am.put(CANCEL_ACTION, new CancelAction());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 */

	private void initComponents() {
		
		entry = new JTextField();
		
		model = new DefaultListModel();
		userArea = new JList(model);
		
		userArea.addMouseListener(new ActionJList(userArea));
		
		status = new JLabel();
		jLabel1 = new JLabel();

		jScrollPane1 = new JScrollPane(userArea);

		jLabel1.setText("Enter text to search:");

		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);

		//Create a parallel group for the horizontal axis
		ParallelGroup hGroup = layout.createParallelGroup(GroupLayout.Alignment.LEADING);

		//Create a sequential and a parallel groups
		SequentialGroup h1 = layout.createSequentialGroup();
		ParallelGroup h2 = layout.createParallelGroup(GroupLayout.Alignment.TRAILING);

		//Add a container gap to the sequential group h1
		h1.addContainerGap();

		//Add a scroll pane and a label to the parallel group h2
		h2.addComponent(jScrollPane1, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE);
		h2.addComponent(status, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE);

		//Create a sequential group h3
		SequentialGroup h3 = layout.createSequentialGroup();
		h3.addComponent(jLabel1);
		h3.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
		h3.addComponent(entry, GroupLayout.DEFAULT_SIZE, 321, Short.MAX_VALUE);

		//Add the group h3 to the group h2
		h2.addGroup(h3);
		//Add the group h2 to the group h1
		h1.addGroup(h2);

		h1.addContainerGap();

		//Add the group h1 to the hGroup
		hGroup.addGroup(GroupLayout.Alignment.TRAILING, h1);
		//Create the horizontal group
		layout.setHorizontalGroup(hGroup);


		//Create a parallel group for the vertical axis
		ParallelGroup vGroup = layout.createParallelGroup(GroupLayout.Alignment.LEADING);
		//Create a sequential group v1
		SequentialGroup v1 = layout.createSequentialGroup();
		//Add a container gap to the sequential group v1
		v1.addContainerGap();
		//Create a parallel group v2
		ParallelGroup v2 = layout.createParallelGroup(GroupLayout.Alignment.BASELINE);
		v2.addComponent(jLabel1);
		v2.addComponent(entry, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
		//Add the group v2 tp the group v1
		v1.addGroup(v2);
		v1.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		v1.addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 233, Short.MAX_VALUE);
		v1.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		v1.addComponent(status);
		v1.addContainerGap();

		//Add the group v1 to the group vGroup
		vGroup.addGroup(v1);
		//Create the vertical group
		layout.setVerticalGroup(vGroup);
	}

	private GraphPan parent;
	
	public void setUserList(List users, GraphPan parent) {
		this.parent = parent;
		if (users == null)
			return;
		this.users.clear();
		for (Iterator it = users.iterator(); it.hasNext(); ) {
			this.users.add(it.next());
		}
		search();
	}
	
	public void search() {
		
		String searchString = entry.getText();

		model.clear();
		if (users.size() > 0) {
			for (Iterator it = users.iterator(); it.hasNext(); ) {
				String u = it.next().toString();
				if ((searchString.length() == 0) || u.toLowerCase().contains(searchString.toLowerCase())) {
					model.addElement(u);
				}
			}
			if (searchString.length() > 0) {
				if (model.size() > 0) {
					entry.setBackground(entryBg);
					message("'" + searchString + "' found.", false);
				} else{
					entry.setBackground(ERROR_COLOR);
					message("'" + searchString + "' not found.", false);
				}
			} else {
				message("", false);
			}
		} else {
			message("No user", true);
		}
		
	}

	void message(String msg, boolean red) {
		status.setText(msg);
		if (red) {
			entry.setEnabled(false);
			userArea.setEnabled(false);
			status.setForeground(Color.red);
			jLabel1.setForeground(Color.red);
		} else {
			entry.setEnabled(true);
			userArea.setEnabled(true);
			status.setForeground(Color.black);
			jLabel1.setForeground(Color.black);
		}
	}

	// DocumentListener methods

	public void insertUpdate(DocumentEvent ev) {
		search();
	}

	public void removeUpdate(DocumentEvent ev) {
		search();
	}

	public void changedUpdate(DocumentEvent ev) {
	}

	class CancelAction extends AbstractAction {
		public void actionPerformed(ActionEvent ev) {

			if (parent != null){
				parent.userSearchMenuList.setVisible(false);
				parent.userSearchHighlight = null;
			}
			
			model.clear();
			for (Iterator it = users.iterator(); it.hasNext(); ) {
				String u = it.next().toString();
				model.addElement(u);
			}
			
			entry.setText("");
			entry.setBackground(entryBg);
		}
	}

	class ActionJList extends MouseAdapter{
		protected JList list;

		public ActionJList(JList l){
			list = l;
		}

		public void mouseClicked(MouseEvent e){
			if(e.getClickCount() == 2){
				int index = list.locationToIndex(e.getPoint());
				ListModel dlm = list.getModel();
				Object item = dlm.getElementAt(index);;
				list.ensureIndexIsVisible(index);
//				System.out.println("Double clicked on " + item);
				if (parent != null){
					parent.userSearchMenuList.setVisible(false);
					parent.userSearchHighlight = item.toString();
					model.clear();
					for (Iterator it = users.iterator(); it.hasNext(); ) {
						String u = it.next().toString();
						model.addElement(u);
					}
					entry.setText("");
					entry.setBackground(entryBg);
				} 
			}
		}
	}

	public static void main(String args[]) {
		//Schedule a job for the event dispatch thread:
		//creating and showing this application's GUI.

		final JFrame f = new JFrame();
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setTitle("SearchFieldDemo");

		//Turn off metal's use of bold fonts
		UIManager.put("swing.boldMetal", Boolean.FALSE);
		SearchUserLayer p = new SearchUserLayer();
		ArrayList l = new ArrayList();
		for (int i=0; i<100; i++)
			l.add("users"+i);
		p.setUserList(l, null);
		f.getContentPane().setLayout(new BorderLayout());
		f.getContentPane().add(p);
		f.setSize(500, 500);
		f.setVisible(true);
	}

	public void focusGained(FocusEvent arg0) {
		entry.requestFocus();
		entry.requestFocusInWindow();
	}

	public void focusLost(FocusEvent arg0) {
	}

} // end of class SearchUserLayer


