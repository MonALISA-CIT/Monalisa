package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.ILink;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;

@SuppressWarnings("restriction")
public class Selector extends lia.Monitor.JiniClient.CommonGUI.GlobePan.Selector {

	BranchGroup peersBranch;
	PickCanvas peersPick;
	protected BranchGroup branch, pingBranch;
	protected PickCanvas picktool, pingPick;

	public Selector(){
		super();
		// this should not be called
	}
	
	public Selector(Canvas3D canvas, BranchGroup branch, BranchGroup pingBranch, BranchGroup peersBranch) {
		super(canvas);
		this.branch = branch;
		this.pingBranch = pingBranch;
		picktool = new PickCanvas(canvas, branch);
		pingPick = new PickCanvas(canvas, pingBranch);
		this.peersBranch = peersBranch;
		this.peersPick = new PickCanvas(canvas, peersBranch);
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

	 public void setPeersBranch(BranchGroup branch){
		  if(this.peersBranch != branch){
			  this.peersBranch = branch;
			  peersPick = new PickCanvas(canvas, branch);
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
		
		  if(peersBranch.isLive()){
			  peersPick.setShapeLocation(x, y);
			  PickResult result = peersPick.pickClosest();
			  //System.out.println("checking for wanLink @ "+x+", "+y+" => "+(result != null));
			  if(result != null){
				  //Object o = result.getObject();
				  ILink link = (ILink) result.getObject().getUserData();
				  //System.out.println(" .... obj = "+o.getClass().toString()+" link = "+link);
				  if(link != null)
					  return link;
			  }			
		  }
		  return null;
	  }

  }
