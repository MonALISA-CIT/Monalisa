package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import lia.util.dataStruct.DoubleLinkedList;
import lia.util.dataStruct.DoubleLinkedListNodeInt;

public class TextureDataSpace {

    static public boolean bDebug = false;

    /** 
     * slabList holds a list of data[] byte arrays, ready to be used by 
     * the texture loading workers. More data[]'s will be created as needed.
     * No caching will be applied to this data structure.
     */
    private final LinkedList<byte[]> slabList;
    /** how big is each data[] array, in bytes */
    private final int slabSize;
    /** how many slabs have been create so far. This shouldn't grow too much :) */
    private volatile int slabCount;

    /**
     * hmPathTextures, hmGlTextures and dllTextures hold the TextureLoadJobResult's 
     * that refer to textures that were loaded in jogl space and then were marked as delete-able. 
     * Instead of being directly deleted from the jogl space, their joglID is stored
     * in TextureLoadJobResult and they can be reused based on a LRU policy.
     */
    private final HashMap hmPathTextures;
    private final DoubleLinkedList dllTextures;
    /** the maximum number of textures in this cache */
    private int maxTextureCacheSize = 30;
    private final LinkedList invalidTextures;

    /** create the texture data space */
    public TextureDataSpace(int slabSize) {
        this.slabList = new LinkedList<byte[]>();
        this.slabSize = slabSize;
        this.slabCount = 0;

        this.hmPathTextures = new HashMap();
        this.dllTextures = new DoubleLinkedList();
        //        this.maxTextureCacheSize = maxTextureCacheSize;
        this.invalidTextures = new LinkedList();
    }

    /** get a data byte[] space for loading a texture */
    public byte[] getSlab() {
        byte[] data = null;
        synchronized (slabList) {
            if (!slabList.isEmpty()) {
                data = slabList.removeLast();
            }
        }
        if (data == null) {
            data = new byte[slabSize];
            slabCount++;
            if (bDebug) {
                System.out.println("creating new slab; " + slabCount + " created so far...");
            }
        } else {
            if (bDebug) {
                System.out.println("reusing slab...");
            }
        }
        return data;
    }

    /** returns the given slab to the pool of existing data slabs */
    public void releaseSlab(byte[] data) {
        synchronized (slabList) {
            slabList.addFirst(data);
            if (bDebug) {
                System.out.println("released slab");
            }
        }
    }

    /** 
     * try to find in cache a texture based on a resultID (tree number + path to texture). 
     * If found, the texture will be removed from the cache
     */
    public JoGLCachedTexture findTexture(String resultID) {
        JoGLCachedTexture result = null;
        synchronized (dllTextures) {
            if ((result = (JoGLCachedTexture) hmPathTextures.remove(resultID)) != null) {
                result.remove(); // removes the result from the dllTextures list;
                if (bDebug) {
                    System.out.println("----[" + dllTextures.getSize() + "]----------- reusing texture "
                            + result.resultID + " -> " + result.joglID);
                }
            }
        }
        return result;
    }

    /**
     * put the given texture in cache to make it available for later use.
     */
    public void cacheTexture(JoGLCachedTexture jct) {
        synchronized (dllTextures) {
            hmPathTextures.put(jct.resultID, jct);
            dllTextures.addFirst(jct);
            if (bDebug) {
                System.out.println("####[" + dllTextures.getSize() + "]############# caching texture " + jct.resultID
                        + " -> " + jct.joglID);
            }
        }
    }

    /** 
     * offer the least recently used textures to be deleted, if they are more than
     * maxTextureCacheSize
     */
    public void offerTexturesToDelete(LinkedBlockingQueue<JoGLCachedTexture> deleteQueue) {
        synchronized (dllTextures) {
            int toDelete = dllTextures.getSize() - maxTextureCacheSize;
            for (int i = 0; i < toDelete; i++) {
                JoGLCachedTexture jct = (JoGLCachedTexture) dllTextures.removeLast();
                hmPathTextures.remove(jct.resultID);
                deleteQueue.offer(jct);
            }
            deleteQueue.addAll(invalidTextures);
            invalidTextures.clear();
        }
    }

    /** 
     * invalidates the textures beloging to the given tree. 
     * These textures will be offered to be deleted at the next call to offerTexturesToDelete 
     */
    public void invalidateTexturesInTree(int treeID) {
        String sTreeID = treeID + "#";
        synchronized (dllTextures) {
            DoubleLinkedListNodeInt elem = dllTextures.getFirst();
            while (elem != null) {
                DoubleLinkedListNodeInt next = elem.hasNext() ? elem.getNext() : null;
                if (((JoGLCachedTexture) elem).resultID.startsWith(sTreeID)) {
                    invalidTextures.add(elem);
                    elem.remove(); // remove from dllTextures
                    hmPathTextures.remove(((JoGLCachedTexture) elem).resultID);
                }
                elem = next;
            }
        }
    }

    /**
     * returns size of cache in megabytes.<br>
     * Transforms from number of textures in megas.
     * @return size of cache in megabytes
     */
    public float getMaxTextureCacheSize() {
        float val = getCacheSize(maxTextureCacheSize);
        if (val > 400) {
            val = 400;
        }
        return val;
    }

    /**
     * sets the number of cached textures based on a cache size in megabytes
     * @param cachesize size of cache in megabytes
     */
    public void setMaxTextureCacheSize(float cachesize) {
        int number = getNoCachedTextures(cachesize);
        if (number < 30) {
            number = 30;
        }
        this.maxTextureCacheSize = number;
    }

    public static int getNoCachedTextures(float cachesize) {
        int number = (int) Math.ceil((((cachesize * 1024) / Texture.OCT_WIDTH / 3) * 1024) / Texture.OCT_HEIGHT);
        return number;
    }

    public static float getMinCacheSize() {
        return getCacheSize(30);
    }

    private static float getCacheSize(int number) {
        int size;
        size = number * Texture.OCT_WIDTH;
        int unit = 0;
        if (size > 1024) {
            size /= 1024;
            unit++;
        }
        ;
        size *= Texture.OCT_HEIGHT * 3;
        if (size > 1024) {
            size /= 1024;
            unit++;
        }
        ;
        if (unit < 2) {
            float new_size = size;
            while (unit < 2) {
                new_size /= 1024;
                unit++;
            }
            ;
            size = (int) (new_size * 10f);
            new_size = size * .1f;
            return new_size;
        }
        return size;
    }
}
