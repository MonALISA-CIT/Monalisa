/**
 * 
 */
package lia.web.servlets.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import lia.web.utils.Annotation;

/**
 * @author costing
 *
 */
public class AnnotationCollection {
	
	private HashMap<String, List<Annotation>> hmAnnotations = new HashMap<>();
	private List<Annotation> lChartAnnotations = new LinkedList<>();
	
	/**
	 * @param l
	 * @param sChartWide
	 */
	public AnnotationCollection(final List<Annotation> l, final String sChartWide){
		if (l==null || l.size()==0)
			return;
		
		final Iterator<Annotation> it = l.iterator();

		// copy only the annotations that apply only for a list of services
		while (it.hasNext()){
			final Annotation a = it.next();
			
			if (a.services.size()==0 || (sChartWide!=null && sChartWide.length()>0 && a.services.contains(sChartWide)) || a.bValue){
				lChartAnnotations.add(a);
			}
			else{
				final Iterator<String> itServices = a.services.iterator();
				
				while (itServices.hasNext()){
					final String sService = itServices.next();
					
					List<Annotation> lAnnotationsPerService = hmAnnotations.get(sService);
					
					if (lAnnotationsPerService==null){
						lAnnotationsPerService = new LinkedList<>();
						
						hmAnnotations.put(sService, lAnnotationsPerService);
					}
					
					lAnnotationsPerService.add(a);
				}
			}
		}
	}
	
	/**
	 * @param sSeries
	 * @return the list of annotations that apply to the given series
	 */
	public List<Annotation> getSeriesAnnotations(final String sSeries){
		return hmAnnotations.get(sSeries);
	}
	
	/**
	 * @return chart-wide annotations
	 */
	public List<Annotation> getChartAnnotations(){
		return lChartAnnotations;
	}
	
}
