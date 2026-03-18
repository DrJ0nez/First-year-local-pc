package dk.itu.group12.bornholm.model;

public abstract class OsmElement {

    private final long id;

    public OsmElement(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
