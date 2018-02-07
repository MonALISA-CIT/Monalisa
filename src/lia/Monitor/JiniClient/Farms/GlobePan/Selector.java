package lia.Monitor.JiniClient.Farms.GlobePan;

import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.LinkHighlightedListener;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.NodeSelectionListener;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.TexturedPie;
import lia.Monitor.monitor.ILink;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;

@SuppressWarnings("restriction")
public class Selector extends lia.Monitor.JiniClient.CommonGUI.GlobePan.Selector  {

  Canvas3D canvas;
  BranchGroup branch, pingBranch, wanBranch;
  PickCanvas picktool, pingPick, wanPick;

  int button = 1;

  Vector listeners = new Vector();
  Vector linkListeners = new Vector();

  public Selector(Canvas3D canvas, BranchGroup branch, BranchGroup pingBranch, BranchGroup wanBranch) {
    this.canvas = canvas;
    this.branch = branch;
	this.pingBranch = pingBranch;
	this.wanBranch = wanBranch;
    picktool = new PickCanvas(canvas, branch);
    pingPick = new PickCanvas(canvas, pingBranch);
	wanPick = new PickCanvas(canvas, wanBranch);
  }

  public void setButton(int b) {
    if(b > 0)
      button = b;
    else if(button == -b)
      if(b == -1)
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

	public void addLinkHighlightedListener(LinkHighlightedListener l){
		linkListeners.add(l);
	}

	public void removeLinkHighlightedListener(LinkHighlightedListener l){
		linkListeners.remove(l);
	}

  public void setNodesBranch(BranchGroup branch) {
    if(this.branch != branch) {
      this.branch = branch;
      picktool = new PickCanvas(canvas, branch);
    }
  }

	public void setPingBranch(BranchGroup branch){
		if(this.pingBranch != branch){
			this.pingBranch = branch;
			pingPick = new PickCanvas(canvas, branch);
		}
	}

	public void setWANBranch(BranchGroup branch){
		if(this.wanBranch != branch){
			this.wanBranch = branch;
			wanPick = new PickCanvas(canvas, branch);
		}
	}

  public rcNode getSelectedNode(int x, int y) {
    if(!branch.isLive())
      return null;

    picktool.setShapeLocation(x, y);
    PickResult result = picktool.pickClosest();
    if(result == null)
      return null;
    else
      return (rcNode) result.getObject().getUserData();
  }

	public Object getSelectedLink(int x, int y) {
		if(pingBranch.isLive()){
			pingPick.setShapeLocation(x, y);
			PickResult result = pingPick.pickClosest();
			//System.out.println("checking for pingLink @ "+x+", "+y+" => "+(result != null));
			if(result != null){
				//Object o = result.getObject();
				ILink link = (ILink) result.getObject().getUserData();
				//System.out.println(" .... obj = "+o.getClass().toString()+" link = "+link);
				if(link != null)
					return link;
			}
		}
		
		if(wanBranch.isLive()){
			wanPick.setShapeLocation(x, y);
			PickResult result = wanPick.pickClosest();
			//System.out.println("checking for wanLink @ "+x+", "+y+" => "+(result != null));
			if(result != null){
				//Object o = result.getObject();
				//ILink link = (ILink) result.getObject().getUserData();
				Object ud = result.getObject().getUserData();
				if(ud instanceof ILink){
					//System.out.println(" .... obj = "+o.getClass().toString()+" link = "+link);
					return (ILink) ud;
				}else if(ud instanceof TexturedPie){
//					System.out.println("router: "+((TexturedPie)ud).name);
					return ud;
				}
			}			
		}
		return null;
	}

//	private getSelectedRouter(int x, y)
	
	public Object getSelectedObject(int x, int y){
		rcNode n = getSelectedNode(x, y);
		if(n != null)
			return n;
		Object l = getSelectedLink(x, y);
		if(l != null){
			return l;
		}
//		TexturedPie r = getSelectedRouter(x, y);
		return null;
	}

  public void mouseClicked(MouseEvent e) {
    if(e.getButton() != button)
        return;

    rcNode node = getSelectedNode(e.getX(), e.getY());
    if(node != null)
      for(int i = 0; i < listeners.size(); i++)
        ((NodeSelectionListener)listeners.get(i)).nodeSelected(node);
  }

  int numMouseEvents = 0;
  public void mouseMoved(MouseEvent e) {
    // Throw out 9 out of every 10 events, to avoid too much wasted processing.
    if(numMouseEvents++ % 5 == 0) {
      Object obj = getSelectedObject(e.getX(), e.getY());
      if(obj == null || obj instanceof rcNode){
		for(int i = 0; i < listeners.size(); i++)
		  ((NodeSelectionListener)listeners.get(i)).nodeHighlighted((rcNode)obj);      	
      }
      if(obj == null || obj instanceof ILink || obj instanceof TexturedPie){
		for(int i = 0; i < linkListeners.size(); i++)
		  ((LinkHighlightedListener)linkListeners.get(i)).linkHighlighted(obj);      	
      	
      }
    }
  }

}
