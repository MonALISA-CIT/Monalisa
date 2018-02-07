
package lia.Monitor.AppControlClient ;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;


public class TiledFill extends Fill
{
    private Fill tile;
    private int tileWidth;
    private int tileHeight;

    public TiledFill(Fill tile, int tileWidth, int tileHeight) {
	this.tile = tile;
	this.tileWidth = tileWidth;
	this.tileHeight = tileHeight;
    }

    public TiledFill() {
	this.tile = null;
	this.tileWidth = -1;
	this.tileHeight = -1;
    }


    public void setTileWidth(int tileWidth) {
	// assert tileWidth > 0
	this.tileWidth = tileWidth;
    }
	
    public int getTileWidth() {
	return tileWidth;
    }

    public void setTileHeight(int tileHeight) {
	// assert tileHeight > 0
	this.tileHeight = tileHeight;
    }

    public int getTileHeight() {
	return tileHeight;
    }

    public void setTile(Fill tile) {
	this.tile = tile;
    }

    public Fill getTile() {
	return tile;
    }

    public void paintFill(Component c, Graphics g, Rectangle r) {
	int x = r.x, y = r.y, w = r.width, h = r.height;
	int tileWidth = getTileWidth();
	int tileHeight = getTileHeight();
	if (tile != null) {
	    Graphics clippedG = g.create(x, y, w, h);
	    Rectangle tr = new Rectangle(tileWidth, tileHeight);
	    for (tr.x = 0; tr.x < w; tr.x += tileWidth) {
		for (tr.y = 0; tr.y < h; tr.y += tileHeight) {
		    tile.paintFill(c, clippedG, tr);
		}
	    }
	    clippedG.dispose();
	}
    }
}
