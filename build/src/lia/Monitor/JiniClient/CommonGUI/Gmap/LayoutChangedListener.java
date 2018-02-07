package lia.Monitor.JiniClient.CommonGUI.Gmap;

public interface LayoutChangedListener {

	public int setElasticLayout(); // should return the total movement produced
	void computeNewLayout(); // should be called by LayoutTransformer to set a layout in more steps
}
