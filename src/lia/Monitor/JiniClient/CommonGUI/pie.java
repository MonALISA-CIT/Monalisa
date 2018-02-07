package lia.Monitor.JiniClient.CommonGUI;
import java.awt.Color;

public class pie {
	public double[] rpie;
	public Color[] cpie;
	public int len;

	public pie(int l) {
		rpie = new double[l];
		cpie = new Color[l];
		len = l;
	}

	public pie(pie p){
		if(p == null)
			return;
		len = p.len;
		rpie = new double[len];
		cpie = new Color[len];
		for(int i=0; i<len; i++){
			rpie[i] = p.rpie[i];
			cpie[i] = new Color(p.cpie[i].getRGB());
		}		
	}

	public void set(pie p) {
		if (p == null)
			return;
		if (len != p.len) {
			len = p.len;
			rpie = new double[len];
			cpie = new Color[len];
		}
		for (int i = 0; i < len; i++) {
			rpie[i] = p.rpie[i];
			cpie[i] = new Color(p.cpie[i].getRGB());
		}
	}
}
