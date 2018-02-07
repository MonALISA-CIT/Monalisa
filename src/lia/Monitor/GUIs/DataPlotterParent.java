package lia.Monitor.GUIs;

import lia.Monitor.Plot.DataPlotter;

public interface DataPlotterParent {
	/** called from a plotter window when it is closed */
	public void stopPlot(DataPlotter win);

}
