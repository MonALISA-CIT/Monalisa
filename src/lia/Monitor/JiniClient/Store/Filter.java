package lia.Monitor.JiniClient.Store;

/**
 * @author costing
 *
 */
public interface Filter {

    /**
     * Filters the data returning a processed value.
     *
     * @param data Result, eResult, ExtendedResult or Vector containing objects of this kind
     * @return a Result, eResult, ExtendedResult or Vector containing objects of this kind, can return <i>null</i> if there's nothing to add
     */
    public Object filterData(Object data);

}
