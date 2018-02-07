package lia.util.algo.Dijkstra.example;

import lia.util.algo.Dijkstra.AdjacencyMatrixGraph;

/**
 * <p>Title: graphs API example</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */

public class LetterGraphBuilder {

    /**
     * We choose to implement the graph with an adjacency matrix
     */
    private AdjacencyMatrixGraph graph;

    /**
     * The object we are going to build
     */
    private LetterGraph letterGraph;

    /**
     *
     * @param numberVertices
     */
    public LetterGraphBuilder(int numberVertices) {

        // There is no controls
        graph = new AdjacencyMatrixGraph(numberVertices);

        letterGraph = new LetterGraph(graph);

        // Add the letter vertices "A", "B", etc ... until the number of vertices
        for (int i = 0; i < numberVertices; i++) {
            /* a bit of explanations about this ugly code :
            'A' is a char !
            'A' + i : in java chars can be added to int
            (char) ('A' + i) : converts into a character the result of addition
            To be crystal clear, A + 1 = B ! Very logic isn't it ?
            Finnaly, valueOf method converts the char into a String object */
            graph.addVertex( String.valueOf( (char) ('A' + i)) );
        }
    }

    /**
     * Add an edge to the graph, wrapper arround LetterGraph addPath method
     * @param start vertex
     * @param end vertex
     * @param weight
     */
    public void addPath(char start, char end, int weight) {
        letterGraph.addPath(String.valueOf(start), String.valueOf(end), weight);
    }

    /**
     *
     * @return the graph which has been built
     */
    public LetterGraph getLetterGraph() {
        return letterGraph;
    }
}