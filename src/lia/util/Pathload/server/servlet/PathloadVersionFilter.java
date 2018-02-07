/**
 * 
 */
package lia.util.Pathload.server.servlet;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Data input into the Proactive Cache is managed by a servlet filter.
 * 
 * @author heri
 *
 */
public class PathloadVersionFilter implements Filter {

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(PathloadVersionFilter.class.getName());

    private FilterConfig filterConfig = null;

    /**
     * Minimum allowed version
     */
    public static int MINIMUM_VERSION = 2;

    /** 
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig arg0) throws ServletException {
        this.filterConfig = arg0;
    }

    /** 
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        if (filterConfig == null) {
            return;
        }

        String ver = request.getParameter("ver");
        String farmName = request.getParameter("farmName");
        if (ver == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINE, "Misformed message from farm " + farmName + " dropped. Version info is missing.");
            }
            return;
        }

        try {
            int version = Integer.parseInt(ver);
            if (version < PathloadVersionFilter.MINIMUM_VERSION) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, "Message from farm " + farmName + " dropped. Minimum allowed version is "
                            + PathloadVersionFilter.MINIMUM_VERSION + " got " + version);
                }
                return;
            }
        } catch (NumberFormatException e) {
            logger.log(Level.FINE, "Message from farm " + farmName
                    + " dropped because of a NumberFormatException while " + "parsing the version number: " + ver);
        }

        chain.doFilter(request, response);
    }

    /** 
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        this.filterConfig = null;
    }

}
