package lia.Monitor.JiniClient.VRVS3D;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.ILink;


public class MST_old {
/** 
    This class implements a Minimum Spanning Tree algorithm
    based on Baruva's algorithm which can be implemented 
    as a distributed alogorithm
*/  

/**
 * inertness factor for a link to avoid fast switching between 
 * similar quality links
 */
public static final double inertness = 1.3;


 Hashtable allNodes ;
 Vector results;
 Vector Trees;

public MST_old (   Hashtable allNodes ) {
   this.allNodes = allNodes ; 
   results = new Vector();
   Trees = new Vector();
}

synchronized public Vector getMST(){
	return results;
}

synchronized public void Compute(){
	Trees.clear();
	if ( allNodes.size() < 2 ) 
		return;
	// initialization phase
	for ( Enumeration e = allNodes.elements() ; e.hasMoreElements() ; ) {
		rcNode node = (rcNode ) e.nextElement();
		// create a subtree for each node containing only the node
		Vector subtree = new Vector();
		subtree.add ( node );
		Trees.add ( subtree );
	}
	results.clear();
	
	boolean madeConnection = true;
	while(madeConnection){
		madeConnection = false;
		// for a subtree
		double bestConn = Double.MAX_VALUE;
		DLink bestTunnel = null;			// in fact, 2 links A-B & B-A
		Vector bestSubtree = null;
		Vector startSubtree = null;

		for(Enumeration e = Trees.elements(); e.hasMoreElements(); ) {
			Vector subtree = (Vector) e.nextElement();
				
			// with all other subtrees, try to find the best tunnel between them
			for(Enumeration e1 = Trees.elements(); e1.hasMoreElements(); ) {
				Vector subtree1 = (Vector) e1.nextElement();
					
				if(subtree == subtree1)
					continue;
					
				// try any node in the first subtree with any other node in the 2nd subtree
				DLink crtTunnel = bestConn(subtree, subtree1);
					
				if(crtTunnel == null)
					continue;
						
				double crtConn = crtTunnel.dist;
				// if and ONLY if I have rttime info about this tunnel proceed
				if(/*(crtConn < 500) &&*/ (bestConn > crtConn)){
						bestConn = crtConn;		// goal: minimize cost
						bestTunnel = crtTunnel;
						startSubtree = subtree;
						bestSubtree = subtree1;							
				}
			}
		}
		if(bestTunnel != null) {
			results.add(bestTunnel);
			// concatenate subtree with subtree1
//			System.out.println("Joining: "+treeToString(startSubtree)
//					+"+ "+treeToString(bestSubtree)
//					+" :: "+bestTunnel[0].from.UnitName +" <-> "
//					+bestTunnel[1].from.UnitName
//					+" = "+bestTunnel[0].getInetRTTime()
//					);
			startSubtree.addAll(bestSubtree);
			Trees.remove(bestSubtree);
			madeConnection = true;
		}
	}
}

/*
ILink [] getBestTunnelPair(Vector st1, Vector st2){
	ILink bestPair [] = new ILink[2];
	double bestCost = Double.MAX_VALUE;
	// for each node in st1 with each in st2 find minimum cost pair
	for(Enumeration e1 = st1.elements(); e1.hasMoreElements(); ){
		rcNode n1 = (rcNode) e1.nextElement();
		for(Enumeration e2 = st2.elements(); e2.hasMoreElements(); ) {
			rcNode n2 = (rcNode) e2.nextElement();
			// check if tunnels from N1 to N2 and back exist
			ILink t12 = (ILink) n1.wconn.get(n2.UnitName);
			ILink t21 = (ILink) n2.wconn.get(n1.UnitName);
			if(t12 == null || t21 == null)
				continue;
			// check if current tunnel pair is better than last one
			double crtCost = (t12.inetQuality != null ? t12.inetQuality[0] : Double.MAX_VALUE)
							+(t21.inetQuality != null ? t21.inetQuality[0] : Double.MAX_VALUE);
			if(bestCost > crtCost){
				bestPair[0] = t12;
				bestPair[1] = t21;
				bestCost = crtCost;
			}
		}
	}
	return (bestCost < Double.MAX_VALUE ? bestPair : null);
}

/*
synchronized  public void Compute( ) {
   boolean made_conn = true; 
   results.clear();
   Trees.clear();

   if ( allNodes.size() < 2 ) return;

   for ( Enumeration e = allNodes.elements() ; e.hasMoreElements() ; ) {
     rcNode node = (rcNode ) e.nextElement();
     Vector subtree = new Vector();
     subtree.add ( node );
     Trees.add ( subtree ) ;
   }


   while ( made_conn ) {
      made_conn = false ;
	 
		for ( Iterator it = Trees.iterator() ; it.hasNext() ; ) {
		   Vector subtree = ( Vector) it.next();
		   Hashtable hdlt = new Hashtable();
		   double best = Double.MAX_VALUE;

		   for ( Iterator it1 = Trees.iterator() ; it1.hasNext() ; ) {
			 Vector subtree1 = ( Vector) it1.next();
			 
			 if ( subtree != subtree1 ) { 

				DLink dl = bestConn ( subtree, subtree1 ) ;
					if ( dl != null ) {
						if ( best > dl.dist ) best = dl.dist;
						hdlt.put(dl, subtree1);
					}
				}//if( dl != null )
			  }//for
			Vector subtree1 = null;
			  
			  if ( hdlt.size() > 0){
			  	for ( Enumeration keys = hdlt.keys(); keys.hasMoreElements(); ) {
			  		DLink dl = (DLink)keys.nextElement();
			  		if ( best == dl.dist ) {
						results.add (dl );
			  			subtree1 = (Vector)hdlt.get(dl);
			  			subtree.addAll(subtree1);
			  			made_conn = true;
			  		}
			  	}
			  		
			  }
			  
				if ( made_conn ) {
					Trees.remove(subtree1);
					break;
				}
			}//for
		}

    //System.out.println ( " T size = " + Trees.size() );
 }
   
*/

 DLink bestConn ( Vector st1, Vector st2 ) {
   rcNode n1f = null;
   rcNode n2f = null;
   double best = Double.MAX_VALUE/2;
   rcNode  n1,n2;
   n1= null; n2= null;
   for ( int i1 =0; i1 < st1.size(); i1++ ) {
     for ( int i2 =0; i2 < st2.size(); i2++ ) {
       n1 = (rcNode) st1.elementAt(i1);
       n2 = (rcNode) st2.elementAt(i2); 
       double val = getPerformance( n1,n2) ;
       if ( val < best ) { 
         n1f = n1;
         n2f = n2;
         best = val;
       }
     }
    }

    if ( n1f == null ) return null;
    DLink dl = new DLink ( n1f.sid, n2f.sid, best ) ;
    return dl;
}

//public Hashtable getAllNodes() { return allNodes ; }

double getPerformance( rcNode n1, rcNode n2 ) {
	
	ILink lnk12 = (ILink) n1.wconn.get(n2.UnitName);
	ILink lnk21 = (ILink) n2.wconn.get(n1.UnitName);
	if (lnk12 == null || lnk21 == null || lnk12.inetQuality == null || lnk21.inetQuality == null) 
		return Double.MAX_VALUE;
	else{
		double crtConn = lnk12.inetQuality[0] + lnk21.inetQuality[0];
		if(lnk12.peersQuality != null) 
			crtConn /= inertness; 	// inertness factor for a link
		if(lnk21.peersQuality != null)     // to avoid fast switching between similar
			crtConn /= inertness;	// quality links
		return crtConn;
	}
}


}





     
