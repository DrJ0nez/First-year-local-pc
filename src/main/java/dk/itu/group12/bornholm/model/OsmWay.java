package dk.itu.group12.bornholm.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.List;

public class OsmWay extends OsmElement {

    private final List<OsmNode> nodes;
    private final HashMap<String, String> tags;

    public OsmWay(long id, List<OsmNode> nodes, HashMap<String, String> tags) {
        super(id);
        this.nodes = nodes;
        this.tags = tags;
    }

    public List<OsmNode> getNodes() {
        return nodes;
    }

    public HashMap<String, String> getTags() {
        return tags;
    }

    public void draw(Graphics2D gc, Color color, float strokeWidth) {
        if (nodes == null || nodes.size() < 2) return;

        gc.setColor(color);

        Path2D.Double path = new Path2D.Double();

        OsmNode first = nodes.get(0);

        if (first == null) return;
        path.moveTo(first.getLon() * 0.56, -first.getLat());

        for (int i = 1; i < nodes.size(); i++) {
            OsmNode node = nodes.get(i);
            if (node == null) continue;

            path.lineTo(node.getLon() * 0.56, -node.getLat());
        }

        boolean isPolygon = nodes.size() >= 3 &&
                first.getId() == nodes.get(nodes.size() - 1).getId();

        if (isPolygon) {
            gc.fill(path);
        } else {
            gc.setStroke(new BasicStroke(strokeWidth));
            gc.draw(path);
        }
    }

    public void drawFilled(Graphics2D gc, Color color) {
        if (nodes == null || nodes.size() < 2) return;

        gc.setColor(color);
        Path2D.Double path = new Path2D.Double();

        OsmNode first = nodes.get(0);
        if (first == null) return;
        path.moveTo(first.getLon() * 0.56, -first.getLat());

        for (int i = 1; i < nodes.size(); i++) {
            OsmNode node = nodes.get(i);
            if (node == null) continue;
            path.lineTo(node.getLon() * 0.56, -node.getLat());
        }

        path.closePath(); // lukker stien selvom den ikke er lukket i OSM
        gc.fill(path);
    }
}