/*
 * Created on Sep 11, 2005 5:11:41 PM
 * Filename: TextureLoadJob.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

public class TextureLoadJob implements Runnable {

    public TextureLoadJobResult result;

    public static final int nSleepTime = 1000;

    public TextureLoadJob(TextureLoadJobResult result) {
        this.result = result;
    }

    @Override
    public void run() {
        try {
            if (result.errReason == TextureLoadJobResult.ERR_ISDOWNLOADING) {
                try {
                    Thread.sleep(nSleepTime);
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }
            Texture.loadNewTextures2(result);
        } catch (Exception ex) {
            //an exception here??? that has to be an error
            System.out.println("Texture Load Worker ERROR: " + result.path);
            ex.printStackTrace();
        }
    }

}
