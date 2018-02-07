package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import javax.swing.ImageIcon;

/** Converts an image from a known format, (such as JPG, PNG...) to a 
 * RAW file: <3bytes:width><3bytes:height><width*height*3<bytes>>
 */
public class Image2RAW {
	String infile;
	String outfile;
	byte [] px3BytesColor;
	
	Image2RAW(String src, String dest){
		infile = src;
		outfile = dest;
	}
	
	void convert(){
		Image image = null;
		System.out.println("Trying to load resource: " + infile);
		try{
			ClassLoader myClassLoader = getClass().getClassLoader();
			URL imageURL = myClassLoader.getResource(infile);
			image = loadImage(imageURL);
		}catch(Exception e){
			System.out.println("Couldn't load resource: " + infile+"\n"+e);
			return;
		}
		System.out.println("Image loaded; preparing it...");
		Component obs = new Component() { };
		int width = image.getWidth(obs);
		int height = image.getHeight(obs);
		BufferedImage buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D big = buffImg.createGraphics();
		big.drawImage(image, 0, 0, obs);
		big.dispose();
		System.out.println("Saving...");
		try{
			File file = new File(outfile);
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			write3Bytes(fos, width);
			write3Bytes(fos, height);
			px3BytesColor = new byte [3*width];
			for(int i=0; i<height; i++){
				for(int j=0; j<width; j++){
					int c = buffImg.getRGB(j, i);
					int base = 3*j;
					px3BytesColor[base] = (byte)((c & 0x00ff0000) >> 16);
					px3BytesColor[base+1] = (byte)((c & 0x0000ff00) >>  8);
					px3BytesColor[base+2] = (byte)(c & 0x000000ff);
				}
				fos.write(px3BytesColor);
				if(i % 100 == 1)
					System.out.println(" ... "+(i*100/height)+"%");
			}
			fos.close();
			System.out.println("Done!");
		}catch(Exception e){
			System.out.println(e);
		}
	}

	private void write3Bytes(FileOutputStream fos, int c) throws IOException {
		byte [] pxColor = new byte[3];
		pxColor[0] = (byte)((c & 0x00ff0000) >> 16);
		pxColor[1] = (byte)((c & 0x0000ff00) >>  8);
		pxColor[2] = (byte)(c & 0x000000ff);
		fos.write(pxColor);
	}
	
	private Image loadImage(URL fileName) {
		ImageIcon icon = new ImageIcon(fileName);

		if (icon.getImageLoadStatus() == MediaTracker.COMPLETE) {
			return icon.getImage();
		} else {
			return null;
		}
	}

	/** first parameter = url of the image; second = destination file */
	public static void main(String[] args) {
		if(args.length == 0){
			System.out.println(" Image2RAW <input_file.{jpg|gif|png}> <output_file.raw>");
			return;
		}
		Image2RAW converter = new Image2RAW(args[0], args[1]);
		converter.convert();
	}
}
