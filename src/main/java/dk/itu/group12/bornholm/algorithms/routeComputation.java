package dk.itu.group12.bornholm.algorithms;

import dk.itu.group12.bornholm.model.*;
import java.util.*;

public class routeComputation {

    /**
     * Creates a graph from each node that pairs to others through
     * edges and weights them after the distance from and to each node
     * @param start is the starting node
     * @param end is the node we want to end at
     * @param osmNodeMap
     * @param osmWayMap
     */
    routeComputation(OsmNode start, OsmNode end, Map<Long, OsmNode> osmNodeMap, Map<Long, OsmWay> osmWayMap) {
        Collection<OsmNode> osmNodes = osmNodeMap.values();
        Collection<OsmWay> osmWays = osmWayMap.values();

        Map<Long, List<Edge>> graph = new HashMap<>();

        // Inserts all nodes in the osmNodeMap into the graph with empty edges
        for (OsmNode node : osmNodes) {
            graph.put(node.getId(), new ArrayList<>());
        }

        // Connects each node in ways to each other. Can exclude ways with specific tags (highway, sidewalk, bike etc.)
        for (OsmWay way : osmWays) {
            /* Not needed for now
            HashMap<String, String> tags = way.getTags();

            // Only include ways that are roads if traveling by car
             if (!tags.containsKey("highway")) continue;
             */

            List<OsmNode> nodes = way.getNodes();
            for (int i = 0; i < nodes.size() - 1; i++) {

                OsmNode a = nodes.get(i);
                OsmNode b = nodes.get(i + 1);

                distTo distTo = new distTo(a, b);
                double dist = distTo.getDist();

                // bidirectional edges
                graph.get(a.getId()).add(new Edge(a.getId(), b.getId(), dist));
                graph.get(b.getId()).add(new Edge(b.getId(), a.getId(), dist));
            }
        }

        // Starts the a* algorithm and calculates the shortest path between two points
        aStar aStar = new aStar(start, end, graph, osmNodeMap);

        // Gets the shortest path returned in an ArrayList of sorted OsmNodes
        ArrayList<OsmNode> shortestPath = aStar.getShortestPath();
    }
}
