<%@ page import="java.io.*,java.awt.*,java.awt.image.*,lazyj.*" %><%!
    private static final Color COLOR_BLUE = new Color(0x00, 0x9A, 0xD2);

    private static final Color COLOR_GREEN = new Color(0x53, 0xE4, 0x00);

    private static final Color COLOR_YELLOW = new Color(0xED, 0xE2, 0x0B);
%><%
    final RequestWrapper rw = new RequestWrapper(request);
    
    final int W = 15;

    final int H = 15;
    
    response.setHeader("Cache-Control", "max-age=604800");
    response.setContentType("image/png");
    
    final BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    
    final Graphics2D g = (Graphics2D) bi.getGraphics();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
    g.setComposite(AlphaComposite.Src);
    
    g.setColor(new Color(255, 255, 255, 0));
    
    g.fillRect(0, 0, W, H);
    
    g.setColor(Color.BLACK);
    
    int r = rw.geti("s", 15)-1;
    
    g.drawOval(0, 0, r, r);
    
    g.setColor(COLOR_GREEN);
    
    g.fillOval(1, 1, r-1, r-1);
    
    final int angle = rw.geti("a");
    
    final int color = rw.geti("c");

    g.setColor(color==1 ? COLOR_BLUE  : COLOR_YELLOW);
        
    g.fillArc(1, 1, r-1, r-1, 0, angle);
    
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    
    org.jfree.chart.encoders.EncoderUtil.writeBufferedImage(bi, "png", baos);
    
    response.setHeader("Content-Length", ""+baos.size());
    
    response.getOutputStream().write(baos.toByteArray());
%>