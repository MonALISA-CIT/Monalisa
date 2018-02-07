package lia.util.algo.Dijkstra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * <p>Title: graphs</p>
 * <p>Description: Interface which specifies a weighted oriented graph.
 * All search algorithms and client classes use that Interface to deal with graphs.
 *  If the implementation changes, no change will be needed in the clients classes
 *   (that's the advantages of using Interfaces !).</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */

public class AdjacencyMatrixGraph implements IGraph {

    /**
     * number of vertices in the graph
     */
    private int verticesNumber;

    /**
     * Map used to map an object contained in a vertex and the index used in the Adjacency Matrix.
     * I.e. for the Graph A---B : int indexA = ((Integer) verticesMap(A)).intValue;
     */
    private HashMap verticesMap;

    /**
     * Array used to map the index used in the Adjacency Matrix and the object contained in a vertex
     * I.e. for the Graph A--B : Object objectA = objectsArray[indexA];
     */
    private Object[] objectsArray;

    /**
     * Current number of vertices contained in the graph
     */
    private int indexCurrentVertex;

    /**
     * Adjacency Matrix AM, with AM[start][destination] = weight.
     * If AM[start][destination] = 0, the path does not exist
     */
    private int[][] adjacencyMatrix;

    /**
     * Adjacency Matrix inversed (mathematical meaning)
     */
    private int[][] adjacencyMatrixInversed;

    /**
     *
     * @param int number of vertices in the graph, must be positive and >0
     */
    public AdjacencyMatrixGraph(int verticesNumber) {
        if ( verticesNumber < 1) {
            throw new IllegalArgumentException("Can't create graph. The verticesNumber must be > 0");
        }

        this.verticesNumber = verticesNumber;
        adjacencyMatrix = new int[verticesNumber][verticesNumber];
        adjacencyMatrixInversed = new int[verticesNumber][verticesNumber];
        verticesMap = new HashMap(verticesNumber);
        objectsArray = new Object[verticesNumber];
        indexCurrentVertex = 0;
    }

    /**
     *
     * @return int the number of vertices in the graph
     */
    public int getVerticesNumber() {
        return verticesNumber;
    }
    /**
     * add a new vertex in the graph
     * @param vertex
     */
    public void addVertex(Object vertex) {
        if ( indexCurrentVertex >= verticesNumber) {
            // Maybe I should throw another exception because it is not the argument which is illegal ...
            throw new IllegalArgumentException("Can't add the vertex because all the vertices have been alreay added");
        }
        verticesMap.put(vertex, Integer.valueOf(indexCurrentVertex));
        objectsArray[indexCurrentVertex] = vertex;
        indexCurrentVertex++;
    }

    /**
     * add an new edge in the graph.
     * @param startVertex should be a vertex in the Graph
     * @param destinationVertex should be a vertex in the Graph
     * @param weight
     */
    public void addEdge(Object startVertex, Object destinationVertex, int weight) {
        int start = getVertexIndex(startVertex);
        int destination = getVertexIndex(destinationVertex);

        if (weight <= 0) {
            throw new IllegalArgumentException("Invalid weight ! " + weight + ". Must be > 0");
        }

        if (weight == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid weight ! " + weight + ". Must be < " + Integer.MAX_VALUE);
        }

        adjacencyMatrix[start][destination] = weight;
        adjacencyMatrixInversed[destination][start] = weight;
    }

    /**
     * Remove an edge
     * @param startVertex
     * @param destinationVertex
     */
    public void removeEdge(Object startVertex, Object destinationVertex) {
        int start = getVertexIndex(startVertex);
        int destination = getVertexIndex(destinationVertex);
        adjacencyMatrix[start][destination] = 0;
        adjacencyMatrixInversed[destination][start] = 0;
    }

    /**
     * Remove an edge to the graph
     * @param startVertex should belong to the Graph
     * @param destinationVertex should belong to the Graph
     * @return boolean
     */
    public boolean edgeExist(Object startVertex, Object destinationVertex) {
        int start = getVertexIndex(startVertex);
        int destination = getVertexIndex(destinationVertex);

        if ( adjacencyMatrix[start][destination] != 0 ) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     *
     * @param vertex
     * @return boolean true if the vertex belongs to the Graph
     */
    public boolean vertexExist(Object vertex) {
        if ( !verticesMap.containsKey(vertex) ) {
            return false;
        }
        else {
            return true;
        }
    }

    /**
     *
     * @param startVertex should belong to the Graph
     * @param destinationVertex should belong to the Graph
     * @return int the weight (distance) of the edge. 0 if the edge does not exist
     */
    public int getEdgeWeight(Object startVertex, Object destinationVertex) {
        int start = getVertexIndex(startVertex);
        int destination = getVertexIndex(destinationVertex);

        return adjacencyMatrix[start][destination];
    }

    /**
     *
     * @param path list of vertices
     * @return int  the total weight (distance) of the path or 0 if the path does not exist
     */
    public int getEdgeWeight(Path path) {

        // if empty or only one element, return 0;
        if (path.getLength() < 1) {
            return 0;
        }

        int total = 0;
        for (int i = 0; i < path.getLength(); i++) {
            int edgeWeigth = getEdgeWeight(path.get(i), path.get(i + 1));
            // if 0, the path does not exist
            if (edgeWeigth == 0 ) {
                return 0;
            }
            else {
                total += edgeWeigth;
            }
        }

        return total;
    }

    /**
     *
     * @param vertex should belong to the Graph
     * @return Iterator on the list of adjacent vertices of the param
     */
    public Iterator getAdjacentVertices(Object vertex) {
        return getAdjacentsFromMatrix(vertex, adjacencyMatrix).iterator();
    }

    /**
     *
     * @param vertex should belong to the Graph
     * @return Iterator on the list of predecessor vertices of the param
     */
    public Iterator getPredecessors(Object vertex) {
        return getAdjacentsFromMatrix(vertex, adjacencyMatrixInversed).iterator();
    }

    /**
     * This method is used by getAdjacentVertices and getPredecessors
     * @param vertex
     * @param matrix Adjacency Matrix inversed or Adjacency Matrix
     * @return ArrayList list of predecessor adjacent / vertices of the param
     */
    private ArrayList getAdjacentsFromMatrix(Object vertex, int[][] matrix) {
        int start = getVertexIndex(vertex);
        ArrayList adjacentVertices = new ArrayList();
        for (int i = 0; i < matrix.length; i++) {
            int weight = matrix[start][i];
            if ( weight != 0) {
                adjacentVertices.add(getVertexObject(i));
            }
        }
        return adjacentVertices;
    }

    /**
     *
     * @param vertex
     * @return int the index used in the Adjacency Matrix
     */
    private int getVertexIndex(Object vertex) {
        if ( !verticesMap.containsKey(vertex) ) {
            throw new IllegalArgumentException("The  vertex ! " + vertex + " does not exist in the graph.");
        }
        return ((Integer) verticesMap.get(vertex)).intValue();
    }

    /**
     *
     * @param index index used in the Adjacency Matrix
     * @return Object return the object contained in the vertex mapped by the param
     */
    private Object getVertexObject(int index) {
        // We don't check the value of the parameter bc this is a private method so I am sure of its value !
        return objectsArray[index];
    }

}
