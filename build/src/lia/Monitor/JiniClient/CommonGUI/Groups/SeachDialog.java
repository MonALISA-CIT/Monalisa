package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.ListModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/**
 * A tooltip class that can be used to dynamically search for a particular farm...
 */
public class SeachDialog extends JToolTip {
	
	JTextField searchString;
	FilteringJList searchList;
	Popup popup = null;
	
	public SeachDialog(final FarmTreePanel farmPanel) {
		super();
		setLayout(new BorderLayout());
		searchList = new FilteringJList();
		JScrollPane pane = new JScrollPane(searchList);
		add(pane, BorderLayout.CENTER);
		searchString = new JTextField();
		searchList.installJTextField(searchString);
		add(searchString, BorderLayout.NORTH); 
		setBorder(BorderFactory.createTitledBorder("Search"));
		setPreferredSize( new Dimension(190, 190));
		KeyAdapter keyAdapter = new KeyAdapter() {
		    public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					farmPanel.regainFocus();
					hidePopup();
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					farmPanel.requestFocus();
					farmPanel.setSelectedFarm((rcNode)searchList.getSelectedValue());
					hidePopup();
				}
				int code = e.getKeyCode();
				if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_LEFT ||
						code == KeyEvent.VK_LEFT || code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_PAGE_UP || 
						code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
					searchList.requestFocus();
					KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(searchList, e);
				} else {
					searchString.requestFocus();
					KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(searchString, e);
				}
		    }
		};
		addKeyListener(keyAdapter);
		KeyAdapter searchListKeyAdapter  = new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					farmPanel.regainFocus();
					hidePopup();
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					farmPanel.requestFocus();
					farmPanel.setSelectedFarm((rcNode)searchList.getSelectedValue());
					hidePopup();
				}
				int code = e.getKeyCode();
				if (!(code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_LEFT ||
						code == KeyEvent.VK_LEFT || code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_PAGE_UP || 
						code == KeyEvent.VK_HOME || code == KeyEvent.VK_END)) {
					searchString.requestFocus();
					KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(searchString, e);
				} 
			}
		};
		searchList.addKeyListener(searchListKeyAdapter);
		KeyAdapter searchStringKeyAdapter  = new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					farmPanel.regainFocus();
					hidePopup();
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					farmPanel.requestFocus();
					farmPanel.setSelectedFarm((rcNode)searchList.getSelectedValue());
					hidePopup();
				}
				int code = e.getKeyCode();
				if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_LEFT ||
						code == KeyEvent.VK_LEFT || code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_PAGE_UP || 
						code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
					searchList.requestFocus();
					KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(searchList, e);
				} 
			}
		};
		searchString.addKeyListener(searchStringKeyAdapter);
	}
	
	public void addNode(rcNode node) {
		if (node == null) return;
		searchList.addElement(node);
	}
	
	public void removeNode(rcNode node) {
		if (node == null) return;
		searchList.delElement(node);
	}
	
	public void showPopup(Component owner, int x, int y) {
		if (owner == null || searchList.getModel().getSize() == 0)
		    return;
//		System.out.println("show called "+x+":"+y);
		if (popup != null) {
			popup.hide();
			popup = null;
		}
		popup = PopupFactory.getSharedInstance().getPopup(owner, this, x, y);
		popup.show();
		requestFocus();
	}
	
	public void hidePopup() {
//		System.out.println("popup 1 called");
		if (popup != null) {
//			System.out.println("popup 2 callled");
			popup.hide();
			popup = null;
		}
	}
	
	public class FilteringJList extends JList {
		private JTextField input;
		
		public FilteringJList() {
			setModel(new FilteringModel());
		}
		
		/**
		 * Associates filtering document listener to text
		 * component.
		 */
		
		public void installJTextField(JTextField input) {
			if (input != null) {
				this.input = input;
				FilteringModel model = (FilteringModel)getModel();
				input.getDocument().addDocumentListener(model);
			}
		}
		
		/**
		 * Disassociates filtering document listener from text
		 * component.
		 */
		
		public void uninstallJTextField(JTextField input) {
			if (input != null) {
				FilteringModel model = (FilteringModel)getModel();
				input.getDocument().removeDocumentListener(model);
				this.input = null;
			}
		}
		
		/**
		 * Doesn't let model change to non-filtering variety
		 */
		
		public void setModel(ListModel model) {
			if (!(model instanceof FilteringModel)) {
				throw new IllegalArgumentException();
			} 
			super.setModel(model);
		}
		
		/**
		 * Adds item to model of list
		 */
		public void addElement(Object element) {
			((FilteringModel)getModel()).addElement(element);
		}
		
		/**
		 * Deletes item to model of list
		 */
		public void delElement(Object element) {
			((FilteringModel)getModel()).delElement(element);
		}
		
		/**
		 * Manages filtering of list model
		 */
		
		private class FilteringModel extends AbstractListModel implements DocumentListener {
			List list;
			List filteredList;
			String lastFilter = "";
			
			public FilteringModel() {
				list = new ArrayList();
				filteredList = new ArrayList();
			}
			
			public void addElement(Object element) {
				list.add(element);
				filter(lastFilter);
			}
			
			public void delElement(Object element) {
				list.remove(element);
				filter(lastFilter);
			}
			
			public int getSize() {
				return filteredList.size();
			}
			
			public Object getElementAt(int index) {
				Object returnValue;
				if (index < filteredList.size()) {
					returnValue = filteredList.get(index);
				} else {
					returnValue = null;
				}
				return returnValue;
			}
			
			void filter(String search) {
				filteredList.clear();
				for (int i=0; i<list.size(); i++) {
					Object element = list.get(i);
					if (element.toString().matches("(?i).*"+search+".*")) {
						filteredList.add(element);
					}
				}
				setSelectedIndex(0);
				fireContentsChanged(this, 0, getSize());
			}
			
			// DocumentListener Methods
			
			public void insertUpdate(DocumentEvent event) {
				Document doc = event.getDocument();
				try {
					lastFilter = doc.getText(0, doc.getLength());
					filter(lastFilter);
				} catch (BadLocationException ble) {
//					System.err.println("Bad location: " + ble);
				}
			}
			
			public void removeUpdate(DocumentEvent event) {
				Document doc = event.getDocument();
				try {
					lastFilter = doc.getText(0, doc.getLength());
					filter(lastFilter);
				} catch (BadLocationException ble) {
//					System.err.println("Bad location: " + ble);
				}
			}
			
			public void changedUpdate(DocumentEvent event) {
			}
		}
	} 
	
	
} // end of class SearchDialog

