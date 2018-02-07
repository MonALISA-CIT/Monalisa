package lia.util.algo.Dijkstra.example;

import java.util.List;

import lia.util.algo.Dijkstra.PathFinder;

/**
 * <p>Title: graphs API example</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */

public class LetterGraphFinder {

    // In fact, class LetterGraphFinder is just a wrapper arround PathFinder
    private PathFinder finder;

    /**
     * @param letterGraph the graph we are going to use !
     */
    public LetterGraphFinder(LetterGraph letterGraph) {
        finder = new PathFinder(letterGraph.getGraph());
    }

    /**
     * @param start letter
     * @param destination letter
     * @return the shortest path in a String format (i.e. A-B-C) or an empty String if no path
     */
    public String findShortestPath(char start, char destination) {
        String s = String.valueOf(start);
        String d = String.valueOf(destination);
        return finder.getShortestPath(s, d).toString();
    }

    /**
     * Explore the graph from a start vertex to a destination vertex and find all the paths which have
     * a total distance <= maxDistance. If the destination is reached, its never mind ! continue again and again
     * until the stop condition.
     * @param start letter
     * @param destination letter
     * @param maxDistance the distance is the total of weights of edges between 2 vertex
     * @return
     */
    public List findPathsWithMaximumDistance(char start, char destination, int maxDistance) {
        String s = String.valueOf(start);
        String d = String.valueOf(destination);

        return finder.findPathsWithMaximumDistance(s, d, maxDistance);
    }
}