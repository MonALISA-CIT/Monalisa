package lia.web.servlets.web;

import java.util.HashMap;
import java.util.Iterator;

import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.data.category.CategoryDataset;

/**
 * @author costing
 *
 */
public class MyCategoryPlot extends CategoryPlot {
	private static final long	serialVersionUID	= 5649246959703964448L;
	
	/**
	 * 
	 */
	HashMap<String, String> hmAlreadyDefined;
    
    /**
     * @param dataset
     * @param domainAxis
     * @param rangeAxis
     * @param renderer
     * @param _hmAlreadyDefined
     */
    public MyCategoryPlot(
	CategoryDataset dataset, 
	CategoryAxis domainAxis, 
	ValueAxis rangeAxis, 
	CategoryItemRenderer renderer, 
	HashMap<String, String> _hmAlreadyDefined)
    {
        super(dataset, domainAxis, rangeAxis, renderer);
	    
        hmAlreadyDefined = _hmAlreadyDefined;
    }
    
    @Override
	public LegendItemCollection getLegendItems(){
        LegendItemCollection lic = new LegendItemCollection();
	    
        Iterator<?> it = super.getLegendItems().iterator();
	    
        while (it.hasNext()){
	    LegendItem li = (LegendItem) it.next();
		
	    if (hmAlreadyDefined.get(li.getLabel())==null){
	        lic.add(li);
	        hmAlreadyDefined.put(li.getLabel(), "");
	    }
	}
	    
	return lic;
    }
    
}
