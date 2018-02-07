/*
 * Created on Sep 22, 2010
 */
package lia.Monitor.Farm;

/**
 *
 * @author ramiro
 */
class ModuleParams {

    final String moduleKey;
    final long repeat;
    final String param;
    
    /**
     * @param repeat
     * @param param
     */
    public ModuleParams(String moduleKey, long repeat, String param) {
        this.moduleKey = moduleKey;
        this.repeat = repeat;
        this.param = param;
    }
    
}
