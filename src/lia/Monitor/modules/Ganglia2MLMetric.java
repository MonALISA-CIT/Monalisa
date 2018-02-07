package lia.Monitor.modules;

public class Ganglia2MLMetric {

    public final String gMetric;
    public final String mlMetric;
    public final int xdrType;
    
    public Ganglia2MLMetric( String gMetric, String mlMetric, int xdrType ) {
        this.gMetric = gMetric;
        this.mlMetric = mlMetric;
        this.xdrType = xdrType;
    }
}
