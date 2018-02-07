package lia.util.algo.Dijkstra.example;

import lia.util.algo.Dijkstra.IGraph;

/**
 * <p>Title: Graphs API Example </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: Unemployed and I think this is not a normal situation :-) Who said that thers is always work for good programmers ?</p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */

public class LetterGraph {

    /**
     * The implementation of the graph is delegated to the Builder class
     */
    private IGraph graph;

    /**
     *
     * @param graph
     * @param numberOfLetters
     */
    public LetterGraph(IGraph graph) {
        this.graph = graph;
    }

    /**
     * Add a new edge to the graph. This method is protected because you are supposed
     * to use the Builder class to build an object of LetterGraph. Protected modifier does
     * not allow others packages to access it !
     * @param start a String of 1 character
     * @param end a String of 1 character
     * @param weight
     */
    protected void addPath(String start, String end, int weight) {
        // The parameters are not checked because I know that class AdjencyMatrixGraph does it for me !
        graph.addEdge(start, end, weight);
    }

    /**
     * This method is protected because you are supposed to access the graph through the FinderClass
     * @return the graph !
     */
    protected IGraph getGraph() {
        return graph;
    }

}