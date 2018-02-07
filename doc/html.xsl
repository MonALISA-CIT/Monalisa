<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version='1.0'>
    <!-- <xsl:import href="http://docbook.sourceforge.net/release/xsl/current/xhtml/docbook.xsl"/> -->
    <!-- optionally, insert your customisations here -->
    <xsl:import href="file:///usr/local/docbook-xsl/html/ldp-html.xsl"/>
    
    <xsl:param name="html.stylesheet" select="'docbook.css'"/>
    <xsl:param name="link.mailto.url" select="'support@monalisa.cacr.caltech.edu'"/>
    <xsl:template name="user.header.content">
    </xsl:template>
    <xsl:template name="user.footer.content">
	<hr/>
	<p align="center"><small>This, and other documents, can be downloaded
    	    from <a href="http://monalisa.cacr.caltech.edu/">http://monalisa.cacr.caltech.edu/</a></small></p>
        <p align="center"><small>For questions about MonALISA, write at 
	    &lt;<a href="mailto:support@monalisa.cern.ch">support@monalisa.cern.ch</a>&gt;.</small></p>
    </xsl:template>
</xsl:stylesheet>
