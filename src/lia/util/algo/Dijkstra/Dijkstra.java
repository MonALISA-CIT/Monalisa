package lia.util.algo.Dijkstra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

/**
 *
 * <p>Title: graphs</p>
 * <p>Description: implementation of the Dijkstra's algorithm to search for
 * the shortest distance in a weighted graph
 * </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Jean-Michel Garnier
 * @version 1.0
 */


public class Dijkstra {

    /**
     * the INFINITE, the maximum weight should be < to this value
     */
    private final static int INFINITE = Integer.MAX_VALUE;

    /**
     * The graph on which we apply the algorithm
     */
    private IGraph graph;

    /**
     * the set of determines vertices V
     */
    private HashSet determinedVerticesSet;

    /**
     * the list of remaining vertices Q
     */
    private PriorityQueue remainingVerticesQueue;

    /**
     * the result map of the shortest path distances from a start vertex after running the algorithm
     */
    private HashMap shortestPathMap;

    /**
     * the result map of predecessors of the shortest path from a start vertex after running the algorithm
     */
    private HashMap predecessorsMap;

    /**
     *
     * @param graph
     */
    public Dijkstra(IGraph graph) {
        this.graph = graph;
        int verticesNumber = graph.getVerticesNumber();

        determinedVerticesSet = new HashSet(verticesNumber);
        remainingVerticesQueue = new PriorityQueue();
        shortestPathMap = new HashMap(verticesNumber);
        predecessorsMap = new HashMap(verticesNumber);
    }

    /**
     * Run the algorihtm
     * see http://ciips.ee.uwa.edu.au/~morris/Year2/PLDS210/dijkstra.html for details
     * @param startVertex
     * @param destinationVertex
     */
    private void runAlgorihtm(Object sourceVertex, Object destinationVertex) {

        // Initialization, empty all data structures
        shortestPathMap.clear();
        predecessorsMap.clear();
        determinedVerticesSet.clear();
        remainingVerticesQueue.clear();

        // add source vertex with a distance of 0
        shortestPathMap.put(sourceVertex, Integer.valueOf(0));

        // Insert source vertex with a distance of 0
        remainingVerticesQueue.insert(sourceVertex, 0);

        // While the priority queue is not empty
        while ( !remainingVerticesQueue.isEmpty() ) {
            // Sort the vertices in the Remaining vertices according to their distance from the source
            // and select the closest one (i.e. dequeue the element with the lowest priority in the queue)
            Object closest = remainingVerticesQueue.dequeueLowestPriorityElement();

            // if the destination is reached, stop the execution
            if ( closest.equals(destinationVertex) ) {
                break;
            }

            //  Add the closest vertex in V-S, to S
            determinedVerticesSet.add(closest);

            // Relaxation, see explanations below
            relax(closest);
        }
    }

    /**
     * The relaxation process updates the costs of all the vertices v connected to a vertex u
     * if we could improve the best estimate of the shortest path to v by including (u,v) in the path to v.
     * @param u vertex whose adjacents vertices should be relaxed
     */
    private void relax(Object vertex) {

        // Iterate on adjacent vertives to the vertex which is relaxed
        Iterator adjacentVertices = graph.getAdjacentVertices(vertex);
        while (adjacentVertices.hasNext()) {
            Object adjVertex = adjacentVertices.next();

            // Do not relax elements which are already determined
            if (!determinedVerticesSet.contains(adjVertex)) {
                // distance = shortest distance from source + distance(vertex, adjacent vertex)
                int distance = getShortestPathFromSource(vertex) +  graph.getEdgeWeight(vertex, adjVertex);

                // Have we found a shortest path ?
                if (getShortestPathFromSource(adjVertex) > distance) {
                    // update shortest path result map
                    setShortestPathFromStart(adjVertex, distance);

                    // update predessors map result
                    predecessorsMap.put(adjVertex, vertex);

                    // re-balance the remaining vertices according to the new shortest distance found
                    remainingVerticesQueue.insert(adjVertex, distance);
                }
            }
        } // end while

    }

    /**
     *
     * @param vertex
     * @return int after running the algorithm with a start vertex,
     * return the distance of the shortet path (to go to param vertex). INFINITE if path does not exist
     */
    private int getShortestPathFromSource(Object vertex) {
        if (shortestPathMap.containsKey(vertex)) {
            return ((Integer) shortestPathMap.get(vertex)).intValue();
        }

        return INFINITE;
    }

    /**
     *
     * @param vertex
     * @param path
     */
    private void setShortestPathFromStart(Object vertex, int path) {
        shortestPathMap.put(vertex, Integer.valueOf(path));
    }

    /**
     *
     * @param start
     * @param destination
     * @return int weight distance of the shortest path or Dijkstra. INFINITE if path does not exist
     */
    public int getShortestWeightDistance(Object start, Object destination) {
        Path shortestPath = getShortestPath(start, destination);
        return graph.getEdgeWeight(shortestPath);
    }

    /**
     *
     * @param start
     * @param destination
     * @return Path of the shortest path or Dijkstra. Empty path if the path does not exist
     */
    public Path getShortestPath(Object start, Object destination) {

        checkVertexExist(start);
        checkVertexExist(destination);

        runAlgorihtm(start, destination);
        // if the start is equals to destination (cyclic path)
        if (!start.equals(destination)) {
            return buildShortestPath(start, destination);
        }
        
        // We apply the Dijkstra algorithm to all the adjacent vertices of start
        PriorityQueue solutionsPQ = new PriorityQueue();
        Iterator iter = graph.getAdjacentVertices(start);
        while (iter.hasNext()) {
            Object vertex = iter.next();
            
            int distFromDestVertex = graph.getEdgeWeight(start, vertex);
            runAlgorihtm(vertex, destination);
            
            solutionsPQ.insert(vertex, distFromDestVertex + getShortestPathFromSource(destination));
        }
        
        // Send the path
        Path path = new Path();
        path.addVertex(start);
        
        // Choose the shortest route among differents path found
        path.addPath(buildShortestPath(solutionsPQ.dequeueLowestPriorityElement(), destination));
        
        return path;
    }

    /**
     * used the predecessorsMap to build the shortest path from a start vertex to a destination vertex
     * @param start
     * @param destination
     * @return Path
     */
    private Path buildShortestPath(Object start, Object destination) {
        Path path = new Path();
        if ( getShortestPathFromSource(destination) != INFINITE ) {
            ArrayList pathList = new ArrayList();
            // we add the destination vertex
            Object predecessor = destination;
            do {
                pathList.add(predecessor);
                predecessor = predecessorsMap.get(predecessor);
            // BUF FIXED 06/12/2002, if the predecessor does not exist, throwed a null pointer exception
            // So, I have added in the stop condition the predecessor != null ...
            } while ( (predecessor != null) && !predecessor.equals(start));

            // don't forget to add the start vertex !
            pathList.add(start);

            // The path is on the wrong side so reverse the list.
            Collections.reverse(pathList);

            return new Path(pathList);
        }

        return path;
    }

    /**
     *
     * @param vertex
     */
    private void checkVertexExist(Object vertex) {
        if ( !graph.vertexExist(vertex) ) {
            throw new IllegalArgumentException("The  vertex ! " + vertex + " does not exist in the graph.");
        }
    }

   /**
    *
    *
    * <p>Title: graphs</p>
    * <p>Description: inner class of Dijkstra used by the Dijkstra's algorithm
    *  to choose the shortest vertex (the one whose priority is the lowest !)
    * </p>
    * <p>Copyright: Copyright (c) 2002</p>
    * <p>Company: </p>
    * @author Jean-Michel Garnier
    * @version 1.0
    */
   public class PriorityQueue {
        /**
         * the queue is implemented by a TreeSet because it includes a sort algorithm !
         */
        private TreeSet queue;

        /**
         *
         */
        public PriorityQueue() {
            queue = new TreeSet();
        }

        /**
         * Remove all the elements of the queue
         */
        public void clear() {
            queue.clear();
        }

        /**
         *
         * @return true if the queue is empty
         */
        public boolean isEmpty() {
            if (queue.isEmpty()) {
                return true;
            }
            return false;
        }

        /**
         *
         * @return int the number of elements in the queue
         */
        public int getSize() {
            return queue.size();
        }

        /**
         *
         * @param element any object !
         * @param priority should be >= 0
         */
        public void insert(Object element, int priority) {
            if (element == null) {
                throw new IllegalArgumentException("element must be not null");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("Illegal distance: " + priority);
            }
            QueueElement queueElement = new QueueElement(element, priority);
            queue.add(queueElement);
        }

        /**
         *
         * @return Object the element in the queue which has the lowest priority. Also removes it from the queue.
         */
        public Object dequeueLowestPriorityElement() {
            if (!isEmpty()) {
                QueueElement queueElement = (QueueElement) queue.first();
                Object element = queueElement.element;
                queue.remove(queueElement);
                return element;
            }
            return null;
        }

        /**
         *
         * <p>Title: graphs</p>
         * <p>Description: element of the Queue. It contains an object and his priority.
         *  Implements Compartable to be used by the TreeSet. </p>
         * <p>Copyright: Copyright (c) 2002</p>
         * <p>Company: </p>
         * @author Jean-Michel Garnier
         * @version 1.0
         */
        class QueueElement implements Comparable {

            /**
             * any object
             */
            public Object element;

            /**
             * Priority. Must be >= 0
             */
            public int priority;

            /**
             *
             * @param element
             * @param priority
             */
            public QueueElement(Object element, int priority) {
                this.element = element;
                this.priority = priority;
            }

            /**
             * Implementation of compareTo
             * @param o
             * @return int a negative integer, zero, or a positive 
             *    integer as the priority of this object is less than, equal to, or 
             * greater than the specified object.
             * @author Modified by Olivier Daroux
             */
            public int compareTo(Object o) {
                QueueElement ps = (QueueElement) o;
                int priorityOther = ps.priority;
                if (this.priority == priorityOther) {
                    if ( this.element.equals(ps.element) ) {
                        return 0;
                    }
                    return -1;
                }
                if (this.priority < priorityOther) {
                    return -1;
                }
                return 1;
            }
        } // End Class QueueElement
    } // End Class PriorityQueue
}


