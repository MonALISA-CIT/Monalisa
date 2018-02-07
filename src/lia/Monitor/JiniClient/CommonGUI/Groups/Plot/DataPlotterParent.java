package lia.Monitor.JiniClient.CommonGUI.Groups.Plot;

public interface DataPlotterParent {
	/** called from a plotter window when it is closed */
	public void stopPlot(MultipleDataPlotter win);

}
