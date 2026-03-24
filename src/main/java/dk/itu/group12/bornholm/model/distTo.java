package dk.itu.group12.bornholm.model;

public class distTo implements Comparable<distTo> {
    OsmNode start;
    OsmNode end;
    double dist;

    /**
     * W.I.P
     * Placeholder to calculate the distance between two points.
     * @param start
     * @param end
     */
    public distTo(OsmNode start, OsmNode end) {
        this.start = start;
        this.end = end;
        // We need the absolute value, so we can get the correct weight of the distance
        double dx = Math.abs(end.getLat() - start.getLat());
        double dy = Math.abs(end.getLon() - start.getLon());
        dist =  Math.sqrt(dx * dx + dy * dy);
    }

    public double getDist() {
        return dist;
    }

    /**
     * allows for Comparable results in a PriorityQueue
     * @param o
     * @return
     */
    @Override
    public int compareTo(distTo o) {
        return Double.compare(dist, o.getDist());
    }
}
