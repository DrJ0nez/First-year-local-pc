package dk.itu.group12.bornholm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dk.itu.group12.bornholm.controller.MapController;
import dk.itu.group12.bornholm.model.OsmNode;
import dk.itu.group12.bornholm.model.OsmWay;
import dk.itu.group12.bornholm.view.DrawingApp;
import dk.itu.group12.bornholm.view.MapFunctions;
import dk.itu.group12.bornholm.view.SuperAffine;
import javafx.scene.Scene;
import javafx.stage.Stage;
import parser.osmParser;

public class App extends DrawingApp {

    private List<OsmWay> ways;
    private final MapFunctions mapFunctions = new MapFunctions();
    private final MapController mapController = new MapController(mapFunctions, this::redraw);

    // Identity transform used to reset Graphics2D between frames
    private static final AffineTransform IDENTITY = new AffineTransform();

    // Coastline path built once at startup
    private Path2D cachedCoastline;

    // Colors
    private static final Color COLOR_SEA      = Color.decode("#aad3df");
    private static final Color COLOR_LAND     = Color.decode("#f0e9dd");
    private static final Color COLOR_FOREST   = Color.decode("#aed1a0");
    private static final Color COLOR_BUILDING = Color.decode("#d9b99b");

    // Pre-categorized way lists – avoids lookups during rendering
    private List<OsmWay> highways;
    private List<OsmWay> forests;
    private List<OsmWay> waters;
    private List<OsmWay> buildings;
    private List<OsmWay> waterways;

    // One label per unique road name, computed at startup
    private List<CachedRoadLabel> cachedRoadLabels;

    // Cached stroke – reused as long as strokeWidth does not change
    private float lastStrokeWidth = -1;
    private BasicStroke cachedStroke;

    // font and stroke for road names
    private static final Font ROAD_NAME_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Color ROAD_NAME_COLOR = new Color(80, 80, 80);
    private static final BasicStroke HALO_STROKE = new BasicStroke(3f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // Cached label for a single road name. Computed at startup, hidden until zoomed in
    private static class CachedRoadLabel {
        final String name;
        // Center point in map coordinates
        final double mapX, mapY;
        // Previous point in map coordinates (used for angle calculation)
        final double prevMapX, prevMapY;
        // Bounding box in map coordinates (used for viewport culling)
        final double bboxMinX, bboxMinY, bboxMaxX, bboxMaxY;

        CachedRoadLabel(String name, double mapX, double mapY,
                        double prevMapX, double prevMapY,
                        double bboxMinX, double bboxMinY,
                        double bboxMaxX, double bboxMaxY) {
            this.name = name;
            this.mapX = mapX;
            this.mapY = mapY;
            this.prevMapX = prevMapX;
            this.prevMapY = prevMapY;
            this.bboxMinX = bboxMinX;
            this.bboxMinY = bboxMinY;
            this.bboxMaxX = bboxMaxX;
            this.bboxMaxY = bboxMaxY;
        }

        boolean isVisible(double vpMinX, double vpMinY, double vpMaxX, double vpMaxY) {
            return bboxMaxX >= vpMinX && bboxMinX <= vpMaxX
                    && bboxMaxY >= vpMinY && bboxMinY <= vpMaxY;
        }
    }

    @Override
    public void start(Stage stage) {
        // 1. Parse data and set up map
        osmParser parser = new osmParser("bornholm/bornholm.osm"); // Can be changed to other maps (bornholm, samsø, tunø etc.)
        parser.parse();
        this.ways = List.copyOf(parser.getOsmWayMap().values());

        // Build coastline once – it never changes
        cachedCoastline = stitchCoastline(ways);

        // Categorize ways once at startup
        highways      = new ArrayList<>();
        forests       = new ArrayList<>();
        waters        = new ArrayList<>();
        buildings     = new ArrayList<>();
        waterways     = new ArrayList<>();

        // Temporarily collect named highways grouped by road name
        HashMap<String, List<OsmWay>> namedHighwayGroups = new HashMap<>();

        for (OsmWay way : ways) {
            Map<String, String> tags = way.getTags();
            String natural = tags.get("natural");
            String landuse = tags.get("landuse");

            if ("coastline".equals(natural)) continue; // handled separately

            if (tags.get("highway") != null) {
                highways.add(way);
                String name = tags.get("name");
                if (name != null) {
                    namedHighwayGroups.computeIfAbsent(name, k -> new ArrayList<>()).add(way);
                }
            } else if ("wood".equals(landuse) || "forest".equals(landuse)) {
                forests.add(way);
            } else if ("water".equals(natural) || "water".equals(landuse)) {
                waters.add(way);
            } else if (tags.get("building") != null) {
                buildings.add(way);
            } else if (tags.get("waterway") != null) {
                waterways.add(way);
            }
        }

        // Build one label per unique road name
        cachedRoadLabels = buildRoadLabels(namedHighwayGroups);

        List<Double> bb = parser.getBoundingBox();
        mapFunctions.reCenter(new double[]{bb.get(1), bb.get(0), bb.get(3), bb.get(2)}, getHEIGHT());

        // 2. Layout – BorderPane with sidebar
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setCenter(this.imageView);
        root.setRight(createSideBar());
        root.setStyle("-fx-background-color: #aad3df;");

        // 3. Show scene
        stage.setTitle("Bornholm Map - Group 12");
        stage.setScene(new Scene(root));
        stage.setWidth(getWIDTH() + 220);
        stage.setHeight(getHEIGHT());
        stage.setResizable(false);
        stage.show();

        mapController.initEvents(this.imageView);
        redraw();
    }

    private void redraw() {
        Graphics2D gc = getNewGraphicsContext();

        // 1. Fill background with sea color in screen coordinates
        gc.setTransform(IDENTITY);
        gc.setColor(COLOR_SEA);
        gc.fillRect(0, 0, getWIDTH(), getHEIGHT());

        // 2. Apply map transform
        SuperAffine transform = mapFunctions.getTransform();
        gc.setTransform(transform);

        float strokeWidth = transform.getStrokeBaseWidth();

        // Update cached stroke only when width changes
        if (strokeWidth != lastStrokeWidth) {
            cachedStroke = new BasicStroke(strokeWidth);
            lastStrokeWidth = strokeWidth;
        }

        // 3. Draw coastline (path is cached)
        gc.setColor(COLOR_LAND);
        gc.fill(cachedCoastline);

        // 4. Compute visible viewport in map coordinates via inverse transform
        Point2D topLeft = transform.inverseTransform(0, 0);
        Point2D bottomRight = transform.inverseTransform(getWIDTH(), getHEIGHT());
        double vpMinX = Math.min(topLeft.getX(), bottomRight.getX());
        double vpMinY = Math.min(topLeft.getY(), bottomRight.getY());
        double vpMaxX = Math.max(topLeft.getX(), bottomRight.getX());
        double vpMaxY = Math.max(topLeft.getY(), bottomRight.getY());

        // 5. Compute zoom level for level-of-detail filtering
        double zoomLevel = Math.abs(transform.getScaleX());

        // 6. Draw only ways visible within the viewport
        for (OsmWay way : forests) {
            if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                way.draw(gc, COLOR_FOREST, strokeWidth, cachedStroke);
        }
        for (OsmWay way : waters) {
            if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                way.draw(gc, COLOR_SEA, strokeWidth, cachedStroke);
        }
        for (OsmWay way : waterways) {
            if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                way.draw(gc, COLOR_SEA, strokeWidth, cachedStroke);
        }

        // Show roads above a zoom threshold
        if (zoomLevel > 3000) {
            for (OsmWay way : highways) {
                if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                    way.draw(gc, Color.GRAY, strokeWidth, cachedStroke);
            }
        }

        // Show buildings above a zoom threshold
        if (zoomLevel > 8000) {
            for (OsmWay way : buildings) {
                if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                    way.draw(gc, COLOR_BUILDING, strokeWidth, cachedStroke);
            }
        }

        // Show road names above a zoom threshold
        if (zoomLevel > 20000) {
            drawRoadNames(gc, transform, vpMinX, vpMinY, vpMaxX, vpMaxY);
        }

        render();
    }

    // Builds cached road labels from grouped named highways.
    // For each unique road name, the segment with the most nodes is used as the representative.
    private List<CachedRoadLabel> buildRoadLabels(HashMap<String, List<OsmWay>> groups) {
        List<CachedRoadLabel> labels = new ArrayList<>();

        for (Map.Entry<String, List<OsmWay>> entry : groups.entrySet()) {
            String name = entry.getKey();
            List<OsmWay> waysForName = entry.getValue();

            // Pick the segment with the most nodes
            OsmWay bestWay = waysForName.get(0);
            int bestSize = bestWay.getNodes() != null ? bestWay.getNodes().size() : 0;
            for (int i = 1; i < waysForName.size(); i++) {
                OsmWay w = waysForName.get(i);
                int size = w.getNodes() != null ? w.getNodes().size() : 0;
                if (size > bestSize) {
                    bestWay = w;
                    bestSize = size;
                }
            }

            List<OsmNode> nodes = bestWay.getNodes();
            if (nodes == null || nodes.size() < 2) continue;

            // Use the midpoint node for label placement
            int midIdx = nodes.size() / 2;
            OsmNode midNode = nodes.get(midIdx);
            OsmNode prevNode = nodes.get(Math.max(0, midIdx - 1));
            if (midNode == null || prevNode == null) continue;

            double mapX = midNode.getLon() * 0.56;
            double mapY = -midNode.getLat();
            double prevMapX = prevNode.getLon() * 0.56;
            double prevMapY = -prevNode.getLat();

            // Compute combined bounding box across all segments with this name
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (OsmWay w : waysForName) {
                if (w.getNodes() == null) continue;
                for (OsmNode n : w.getNodes()) {
                    if (n == null) continue;
                    double x = n.getLon() * 0.56;
                    double y = -n.getLat();
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }

            labels.add(new CachedRoadLabel(name, mapX, mapY, prevMapX, prevMapY,
                    minX, minY, maxX, maxY));
        }

        return labels;
    }

    private void drawRoadNames(Graphics2D gc, SuperAffine transform,
                               double vpMinX, double vpMinY, double vpMaxX, double vpMaxY) {
        gc.setTransform(IDENTITY);
        gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        gc.setFont(ROAD_NAME_FONT);

        FontRenderContext frc = gc.getFontRenderContext();

        // Track screen rectangles of already placed labels to avoid overlaps
        List<Rectangle2D> placedLabels = new ArrayList<>();

        for (CachedRoadLabel label : cachedRoadLabels) {
            if (!label.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY)) continue;

            // Convert map coordinates to screen coordinates
            double[] screenPt = new double[2];
            transform.transform(new double[]{label.mapX, label.mapY}, 0, screenPt, 0, 1);
            double sx = screenPt[0];
            double sy = screenPt[1];

            // Skip if off screen
            if (sx < -100 || sx > getWIDTH() + 100 || sy < -20 || sy > getHEIGHT() + 20) continue;

            // Compute road angle for text rotation
            double[] prevScreenPt = new double[2];
            transform.transform(new double[]{label.prevMapX, label.prevMapY}, 0, prevScreenPt, 0, 1);

            double angle = Math.atan2(screenPt[1] - prevScreenPt[1], screenPt[0] - prevScreenPt[0]);

            // Keep text readable (never upside-down)
            if (angle > Math.PI / 2)  angle -= Math.PI;
            if (angle < -Math.PI / 2) angle += Math.PI;

            // Measure text for collision detection
            GlyphVector gv = ROAD_NAME_FONT.createGlyphVector(frc, label.name);
            Rectangle2D textBounds = gv.getLogicalBounds();
            double textWidth = textBounds.getWidth();
            double textHeight = textBounds.getHeight();

            // Compute axis-aligned bounding box of the rotated label
            double cos = Math.abs(Math.cos(angle));
            double sin = Math.abs(Math.sin(angle));
            double aabbW = textWidth * cos + textHeight * sin;
            double aabbH = textWidth * sin + textHeight * cos;

            // Label screen rectangle centered on (sx, sy) with padding
            double padding = 4;
            Rectangle2D labelRect = new Rectangle2D.Double(
                    sx - aabbW / 2 - padding, sy - aabbH / 2 - padding,
                    aabbW + 2 * padding, aabbH + 2 * padding);

            // Skip label if it overlaps an already placed one
            boolean collides = false;
            for (Rectangle2D placed : placedLabels) {
                if (placed.intersects(labelRect)) {
                    collides = true;
                    break;
                }
            }
            if (collides) continue;

            // No collision – register and draw
            placedLabels.add(labelRect);

            AffineTransform textTransform = new AffineTransform();
            textTransform.translate(sx, sy);
            textTransform.rotate(angle);
            textTransform.translate(-textWidth / 2, 0);

            gc.setTransform(textTransform);

            // Draw white halo behind text
            gc.setColor(Color.WHITE);
            gc.setStroke(HALO_STROKE);
            gc.draw(gv.getOutline());

            // Draw text on top
            gc.setColor(ROAD_NAME_COLOR);
            gc.fill(gv.getOutline());

            gc.setTransform(IDENTITY);
        }
    }

    /** Builds the coastline Path2D – called once at startup */
    private Path2D stitchCoastline(List<OsmWay> ways) {
        List<List<OsmNode>> segments = new ArrayList<>();
        for (OsmWay way : ways) {
            if ("coastline".equals(way.getTags().get("natural")) && way.getNodes() != null) {
                segments.add(new ArrayList<>(way.getNodes()));
            }
        }

        if (segments.isEmpty()) return new Path2D.Double();

        // WIND_EVEN_ODD ensures islands don't cancel each other out
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        while (!segments.isEmpty()) {
            // Start a new ring from the first available segment
            List<OsmNode> ring = new ArrayList<>(segments.remove(0));

            // Stitch segments until the ring is closed or no match is found
            boolean extended = true;
            while (extended) {
                extended = false;
                OsmNode last = ring.get(ring.size() - 1);
                for (int i = 0; i < segments.size(); i++) {
                    if (segments.get(i).get(0).getId() == last.getId()) {
                        ring.addAll(segments.get(i).subList(1, segments.get(i).size()));
                        segments.remove(i);
                        extended = true;
                        break;
                    }
                }
            }

            // Add this ring as a subpath
            OsmNode first = ring.get(0);
            if (first == null) continue;
            path.moveTo(first.getLon() * 0.56, -first.getLat());

            for (int i = 1; i < ring.size(); i++) {
                OsmNode n = ring.get(i);
                if (n == null) continue;
                path.lineTo(n.getLon() * 0.56, -n.getLat());
            }
            path.closePath(); // Close this island and prepare for the next
        }

        return path;
    }

    private javafx.scene.layout.VBox createSideBar() {

        double maxZoomSlider = 100.0;
        double minZoomSlider = 0.1;
        double startZoomSlider = 1.0;

        double maxSeaSlider = 10.0;
        double minSeaSlider = -10.0;
        double startSeaSlider = 0.0;

        // Create elements
        javafx.scene.text.Text zoomLabel = new javafx.scene.text.Text("Zoom: 100%");
        zoomLabel.setFill(javafx.scene.paint.Color.DARKSLATEGRAY);

        javafx.scene.control.Slider zoomSlider = new javafx.scene.control.Slider(minZoomSlider, maxZoomSlider, startZoomSlider);

        javafx.scene.text.Text seaLevelLabel = new javafx.scene.text.Text("Sea Level: 0.0m");
        seaLevelLabel.setFill(javafx.scene.paint.Color.DARKSLATEGRAY);

        javafx.scene.control.Slider seaLevelSlider = new javafx.scene.control.Slider(minSeaSlider, maxSeaSlider, startSeaSlider);

        mapController.sliders(zoomSlider, zoomLabel, seaLevelSlider, seaLevelLabel);

        // Design & Styling
        javafx.scene.layout.VBox sideBar = new javafx.scene.layout.VBox(15);
        sideBar.setPadding(new javafx.geometry.Insets(25));
        sideBar.setPrefWidth(220);
        sideBar.setStyle("-fx-background-color: rgba(255, 255, 255, 0.3); " +
                "-fx-background-radius: 15 0 0 15; " +
                "-fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 0 0 0 1;");

        sideBar.getChildren().addAll(zoomLabel, zoomSlider, seaLevelLabel, seaLevelSlider);

        return sideBar;
    }
}