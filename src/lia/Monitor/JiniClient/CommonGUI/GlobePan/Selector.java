package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.media.j3d.Canvas3D;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.ILink;

@SuppressWarnings("restriction")
public class Selector extends Manipulator {

	protected Canvas3D canvas;

	protected int button = 1;

	protected Vector listeners = new Vector();
	protected Vector linkListeners = new Vector();

	public Selector() {
		// this should not be called
	}

	public Selector(Canvas3D canvas) {
		this.canvas = canvas;
	}

	public void setButton(int b) {
		if (b > 0)
			button = b;
		else if (button == -b)
			if (b == -1)
				button = 0;
			else
				button = 1;
	}

	public void addNodeSelectionListener(NodeSelectionListener l) {
		listeners.add(l);
	}

	public void removeNodeSelectionListener(NodeSelectionListener l) {
		listeners.remove(l);
	}

	public void addLinkHighlightedListener(LinkHighlightedListener l) {
		linkListeners.add(l);
	}

	public void removeLinkHighlightedListener(LinkHighlightedListener l) {
		linkListeners.remove(l);
	}

	/** this should be redefined */
	public rcNode getSelectedNode(int x, int y) {
		return null;
	}

	/** this should be redefined */
	public Object getSelectedLink(int x, int y) {
		return null;
	}

	public Object getSelectedObject(int x, int y) {
		rcNode n = getSelectedNode(x, y);
		if (n != null)
			return n;
		Object l = getSelectedLink(x, y);
		if (l != null) {
			// System.out.println("found link: "+ l);
			return l;
		}
		return null;
	}

	public void mouseClicked(MouseEvent e) {
		if (e.getButton() != button)
			return;

		rcNode node = getSelectedNode(e.getX(), e.getY());
		if (node != null)
			for (int i = 0; i < listeners.size(); i++)
				((NodeSelectionListener) listeners.get(i)).nodeSelected(node);
	}

	int numMouseEvents = 0;

	public void mouseMoved(MouseEvent e) {
		// Throw out 9 out of every 10 events, to avoid too much wasted
		// processing.
		if (numMouseEvents++ % 5 == 0) {
			Object obj = getSelectedObject(e.getX(), e.getY());
			if (obj == null || obj instanceof rcNode) {
				for (int i = 0; i < listeners.size(); i++)
					((NodeSelectionListener) listeners.get(i)).nodeHighlighted((rcNode) obj);
			}
			if (obj == null || obj instanceof ILink) {
				for (int i = 0; i < linkListeners.size(); i++)
					((LinkHighlightedListener) linkListeners.get(i)).linkHighlighted(obj);

			}
		}
	}

}
