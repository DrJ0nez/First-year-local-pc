package dk.itu.group12.bornholm.Interfaces;

import dk.itu.group12.bornholm.model.OsmNode;

public interface Haversine {
    double h(OsmNode a, OsmNode b);
}

