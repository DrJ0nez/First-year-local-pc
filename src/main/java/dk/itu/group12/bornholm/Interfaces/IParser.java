package dk.itu.group12.bornholm.Interfaces;

import java.util.HashMap;
import java.util.List;

import dk.itu.group12.bornholm.model.OsmNode;
import dk.itu.group12.bornholm.model.OsmRelation;
import dk.itu.group12.bornholm.model.OsmWay;

public interface IParser {

    void parse();

    HashMap<Long, OsmNode> getOsmNodeMap();

    HashMap<Long, OsmWay> getOsmWayMap();

    HashMap<Long, OsmRelation> getOsmRelationMap();

    List<Double> getBoundingBox();
}

