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

    // Cached path – bygges kun én gang, genbruges ved hver redraw
    private Path2D.Double cachedPath;
    private boolean isPolygon;

    // Bounding box i map-koordinater (lon*0.56, -lat) – beregnes én gang
    private double bboxMinX, bboxMinY, bboxMaxX, bboxMaxY;
    private boolean bboxComputed;

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

    //Beregner og cacher bounding box fra nodes.
    private void computeBBox() {
        if (bboxComputed) return;
        bboxComputed = true;
        if (nodes == null || nodes.size() < 2) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (OsmNode node : nodes) {
            if (node == null) continue;
            double x = node.getLon() * 0.56;
            double y = -node.getLat();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        bboxMinX = minX;
        bboxMinY = minY;
        bboxMaxX = maxX;
        bboxMaxY = maxY;
    }

    //Tjekker om denne way overlapper med det synlige viewport
    public boolean isVisible(double vpMinX, double vpMinY, double vpMaxX, double vpMaxY) {
        computeBBox();
        return bboxMaxX >= vpMinX && bboxMinX <= vpMaxX
                && bboxMaxY >= vpMinY && bboxMinY <= vpMaxY;
    }

    //Bygger og cacher Path2D fra nodes.
    private Path2D.Double getPath() {
        if (cachedPath != null) return cachedPath;
        if (nodes == null || nodes.size() < 2) return null;

        OsmNode first = nodes.get(0);
        if (first == null) return null;

        cachedPath = new Path2D.Double();
        cachedPath.moveTo(first.getLon() * 0.56, -first.getLat());

        for (int i = 1; i < nodes.size(); i++) {
            OsmNode node = nodes.get(i);
            if (node == null) continue;
            cachedPath.lineTo(node.getLon() * 0.56, -node.getLat());
        }

        isPolygon = nodes.size() >= 3 &&
                first.getId() == nodes.get(nodes.size() - 1).getId();

        return cachedPath;
    }

    public void draw(Graphics2D gc, Color color, float strokeWidth, BasicStroke sharedStroke) {
        Path2D.Double path = getPath();
        if (path == null) return;

        gc.setColor(color);

        if (isPolygon) {
            gc.fill(path);
        } else {
            gc.setStroke(sharedStroke);
            gc.draw(path);
        }
    }

    public void draw(Graphics2D gc, Color color, float strokeWidth) {
        draw(gc, color, strokeWidth, new BasicStroke(strokeWidth));
    }

    public void drawFilled(Graphics2D gc, Color color) {
        if (nodes == null || nodes.size() < 2) return;

        gc.setColor(color);

        Path2D.Double path = getPath();
        if (path == null) return;

        // Luk stien ved at tegne til startpunktet hvis den ikke allerede er lukket
        if (!isPolygon) {
            Path2D.Double closedPath = new Path2D.Double(path);
            closedPath.closePath();
            gc.fill(closedPath);
        } else {
            gc.fill(path);
        }
    }
}