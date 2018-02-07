package lia.util.algo.Dijkstra;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Title: graphs</p>
 * <p>Description: contains all the search methods.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */

public class PathFinder {

    /**
     * the graph we are gonna to explore
     */
    private IGraph graph;

    /**
     * We need our favourite algorithm
     */
    private Dijkstra dijkstra;

    /**
     * Where we gonna go now ?
     */
    private Object destination;

    /**
     * The lenght of a path is different from its distance. i.e. : lenght(A-B-C) = 2 !
     */
    private int maxLength;

    /**
     * The total distance. distance(A-B-C) = distance(A-B) + distance(B-C)
     */
    private int maxDistance;

    /**
     * list of Path
     */
    private ArrayList solutionsList;

    /**
     *
     * @param graph
     */
    public PathFinder(IGraph graph) {
        this.graph = graph;
        dijkstra = new Dijkstra(graph);
        solutionsList = new ArrayList();
    }

    /**
     * wrapper around Dijkstra method
     * @param start
     * @param destination
     * @return
     */
    public int getShortestWeightDistance(Object start, Object destination) {
        return dijkstra.getShortestWeightDistance(start, destination);
    }

    /**
     * wrapper around Dijkstra method
     * @param start
     * @param destination
     * @return
     */
    public Path getShortestPath(Object start, Object destination) {
        return dijkstra.getShortestPath(start, destination);
    }

    /**
     *
     * @param start
     * @param destination
     * @param maxLength
     * @return list of paths
     */
    public List findPathsWithMaximumLength(Object start, Object destination, int maxLength) {
        findPaths(start, destination, maxLength);
        return solutionsList;
    }

    /**
     *
     * @param start
     * @param destination
     * @param exactLength
     * @return list of paths
     */
    public List findPathsWithExactLength(Object start, Object destination, int exactLength) {

        findPaths(start, destination, exactLength);

        // Remove all the paths whose lenght is < exactLength
        ArrayList filteredSolution = new ArrayList();
        Iterator iter = solutionsList.iterator();
        while (iter.hasNext()) {
            Path path = (Path) iter.next();
            if ( path.getLength() == exactLength ) {
                filteredSolution.add(path);
            }
        }
        return filteredSolution;
    }

    /**
     * Explore the graph from a start vertex to a destination vertex and find all the paths which have
     * a total distance <= maxDistance. If the destination is reached, its never mind ! continue again and again
     * until the stop condition.
     * @param start
     * @param destination
     * @param maxDistance
     * @return
     */
    public List findPathsWithMaximumDistance(Object start, Object destination, int maxDistance) {
        this.destination = destination;
        this.maxDistance = maxDistance;

        Path rootPath = new Path();
        rootPath.addVertex(start);
        solutionsList.clear();

        searchDistance(rootPath);
        return solutionsList;
    }

    /**
     * Explore the graph from a start vertex to a destination vertex and find all the paths which have
     * a  length <= maxLength. If the destination is reached, its never mind ! continue again and again
     * until the stop condition.
     * @param start
     * @param destination
     * @param maxLength
     */
    private void findPaths(Object start, Object destination, int maxLength) {
        // @TODO I have not test this controls
        if ( !graph.vertexExist(start) ) {
            throw new IllegalArgumentException("The  vertex ! " + start + " does not exist in the graph.");
        }
        if ( !graph.vertexExist(destination) ) {
            throw new IllegalArgumentException("The  vertex ! " + destination + " does not exist in the graph.");
        }

        this.destination = destination;
        this.maxLength = maxLength;

        Path rootPath = new Path();
        rootPath.addVertex(start);
        solutionsList.clear();

        searchLength(rootPath);
    }

    /**
     * Recursive method. Explore the graph from one starting path and store all the paths that reach the destination
     * Stop its exploration it a path length is > maxLenght
     * @param path
     */
    private void searchLength(Path path) {
        // Stop condition : If the path length is > maxLenght
        if (path.getLength() < maxLength) {
            Object lastVertex = path.getLast();
            Iterator iterAdjacents =graph.getAdjacentVertices(lastVertex);
            while (iterAdjacents.hasNext()) {
                Object adjacentVertex = iterAdjacents.next();
                // We clone the input path to create the new path
                Path newPath = (Path) path.clone();
                newPath.addVertex(adjacentVertex);

                if ( adjacentVertex.equals(destination) ) {
                    // add in the solutions list
                    solutionsList.add(newPath);
                }

                searchLength(newPath);
            }
        } // end if Stop Condition
    }

    /**
     * Recursive method. Explore the graph from one starting path and store all the paths that reach the destination
     * Stop its exploration it a the path distance is > maxDistance
     * @param path
     */
    private void searchDistance(Path path) {
        Object lastVertex = path.getLast();

        // Stop condition : If the path distance is > maxDistance
        if (graph.getEdgeWeight(path) + dijkstra.getShortestWeightDistance(lastVertex, destination) <= maxDistance) {

            Iterator iterAdjacents =graph.getAdjacentVertices(lastVertex);
            while (iterAdjacents.hasNext()) {
                Object adjacentVertex = iterAdjacents.next();
                // We clone the input path to create the new path
                Path newPath = (Path) path.clone();
                newPath.addVertex(adjacentVertex);

                if ( adjacentVertex.equals(destination) && graph.getEdgeWeight(newPath) <= maxDistance) {
                    // add in the solutions list
                    solutionsList.add(newPath);
                }

                searchDistance(newPath);
            }
        } // end if Stop Condition
    }
}
