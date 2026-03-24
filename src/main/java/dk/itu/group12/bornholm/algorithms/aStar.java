package dk.itu.group12.bornholm.algorithms;

import dk.itu.group12.bornholm.Interfaces.Haversine;
import dk.itu.group12.bornholm.model.*;

import java.util.*;

public class aStar implements Haversine {
    private final Map<Long, List<Edge>> graph;
    private final Map<Long, OsmNode> osmNodeMap;
    private final OsmNode start;
    private final OsmNode end;

    ArrayList<OsmNode> shortestPath = new ArrayList<>();

    /**
     * The a* algorithm uses heuristics in dijkstra's algorithm to calculate the shortest path faster W.I.P
     * @param start node
     * @param end node
     * @param graph all node IDs and their edges between other nodes (saved as IDs) and their weight between points
     * @param osmNodeMap
     */
    aStar(OsmNode start, OsmNode end, Map<Long, List<Edge>> graph,  Map<Long, OsmNode> osmNodeMap) {
        this.start = start;
        this.end = end;
        this.graph = graph;
        this.osmNodeMap = osmNodeMap;

        runAStar();
    }

    /**
     * Currently the skeleton for running the calculations and algorithmic choices
     */
    private void runAStar() {

    }

    /**
     * returns the shortest path between the two points given in a sorted order as an ArrayList of OsmNodes
     * @return
     */
    public ArrayList<OsmNode> getShortestPath() {
        return shortestPath;
    }

    /**
     * This is the haversine formula for distance between two points on a sphere
     * @param from
     * @param to
     * @return
     */
    @Override
    public double h(OsmNode from, OsmNode to) {
        // https://www.geeksforgeeks.org/dsa/haversine-formula-to-find-distance-between-two-points-on-a-sphere/
            double lat1 = from.getLat(), lon1 = from.getLon();
            double lat2 = to.getLat(), lon2 = to.getLon();

            // distance between latitudes and longitudes
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            // convert to radians
            lat1 = Math.toRadians(lat1);
            lat2 = Math.toRadians(lat2);

            // apply formulae
            double a = Math.pow(Math.sin(dLat / 2), 2) +
                    Math.pow(Math.sin(dLon / 2), 2) *
                            Math.cos(lat1) *
                            Math.cos(lat2);
            double rad = 6371;
            double c = 2 * Math.asin(Math.sqrt(a));
            return rad * c;
    }
}
