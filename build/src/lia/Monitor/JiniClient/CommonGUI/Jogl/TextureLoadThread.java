package lia.Monitor.JiniClient.CommonGUI.Jogl;

/*
 * Created on 09.05.2004 13:42:32
 * Filename: TextureLoadThread.java
 *
 */
/**
 * @author Luc
 *
 * TextureLoadThread
 * loads textures from file to memory
 */
public class TextureLoadThread extends Thread 
{
    public TextureLoadThread() {
        super("( ML ) - TextureLoadThread");
    }
	public void run()
	{
		try {
//			Texture.loadNewTextures();
            Texture.loadNewTextures1();
		} catch ( Exception ex ) {
		    System.out.println("Error loading new textures: ");
		    ex.printStackTrace();
		}
	}
}
