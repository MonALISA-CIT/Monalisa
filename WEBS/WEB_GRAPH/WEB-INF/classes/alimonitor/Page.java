package alimonitor;

import java.io.OutputStream;

public class Page extends lia.web.utils.Page {

    private static final String PATH = "/home/monalisa/MLrepository/tomcat/webapps/ROOT/";

    public Page(){
	super();
    }
    
    public Page(final String sTemplateFile){
	super(PATH + sTemplateFile);
    }
    
    public Page(final OutputStream osOut, final String sTemplateFile){
	super(osOut, PATH + sTemplateFile);
    }

    public Page(final OutputStream osOut, final String sTemplateFile, final boolean bCached){
	super(osOut, PATH + sTemplateFile, false, bCached);
    }

    public Page(final String sTemplateFile, final boolean bCached){
	super(null, PATH + sTemplateFile, false, bCached);
    }

}
