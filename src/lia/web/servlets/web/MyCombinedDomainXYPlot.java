package lia.web.servlets.web;

import java.util.HashMap;
import java.util.Iterator;

import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;

/**
 * @author costing
 *
 */
public class MyCombinedDomainXYPlot extends CombinedDomainXYPlot {
	private static final long	serialVersionUID	= 1L;

	/**
	 * 
	 */
	public MyCombinedDomainXYPlot() {
		super();
	}

	/**
	 * @param va
	 */
	public MyCombinedDomainXYPlot(ValueAxis va) {
		super(va);
	}

	@Override
	public LegendItemCollection getLegendItems() {
		HashMap<String, String> hmAlreadyDefined = new HashMap<String, String>();

		LegendItemCollection lic = new LegendItemCollection();

		Iterator<?> it = super.getLegendItems().iterator();

		while (it.hasNext()) {
			LegendItem li = (LegendItem) it.next();

			if (hmAlreadyDefined.get(li.getLabel()) == null) {
				lic.add(li);
				hmAlreadyDefined.put(li.getLabel(), "");
			}
		}

		return lic;
	}

}
