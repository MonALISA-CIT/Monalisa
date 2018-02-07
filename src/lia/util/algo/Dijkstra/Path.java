package lia.util.algo.Dijkstra;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Title: graphs</p>
 * <p>Description: generalization of the route concept.
 * It is a list of Objects ( i.e. O1--O2--O3--04). Cloneable.
 *
 * </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 */

public class Path implements Cloneable{

    /**
     * List which contains the vertices
     */
    private ArrayList verticesList;

    /**
     *
     */
    public Path() {
        verticesList = new ArrayList();
    }

    /**
     *
     * @param list
     */
    public Path(List list) {
        verticesList = (ArrayList) list;
    }

    /**
     *
     * @return
     */
    public int getLength() {
        return verticesList.size() - 1;
    }

    /**
     *
     * @param vertex should be cloneable
     * @return this path
     */
    public Path addVertex(Object vertex) {
        verticesList.add(vertex);
        return this;
    }

    /**
     *
     * @param path
     * @return this path
     */
    public Path addPath(Path path) {
        verticesList.addAll(path.verticesList);
        return this;
    }

    /**
     *
     * @param index
     * @return the object at position index in the path
     */
    public Object get(int index) {
        return verticesList.get(index);
    }

    /**
     *
     * @return last object of the path
     */
    public Object getLast() {
        return verticesList.get(verticesList.size()-1);
    }

    /**
     * clone the path, all the objects in the path should be cloneables
     * @return the cloned path
     */
    public Object clone() {
        Path newInstance = null;
        try {
            newInstance = (Path) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // the objects in the path are supposed to be cloneable
            e.printStackTrace();
        }

        newInstance.verticesList = (ArrayList) verticesList.clone();
        return newInstance;
    }

    /**
     *
     * @return a list of all the vertices of the path separed by -
     * i.e. Path[A, B, C] --> "A-B-C"
     */
    public String toString() {
        String s = new String();
        Iterator iter = verticesList.iterator();
        if (iter.hasNext()) {
            Object item = iter.next();
            s += item.toString();
        }
        while (iter.hasNext()) {
            Object item = iter.next();
            s += "-" + item.toString();
        }
        return s;
    }
}
