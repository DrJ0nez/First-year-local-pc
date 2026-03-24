package dk.itu.group12.bornholm.model;

public class Edge {
    private final long from;
    private final long to;
    private final double weight;

    /**
     * Is the edge from a point to
     * @param from
     * @param to
     * @param weight
     */
    public Edge(long from,long to, double weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }
}
