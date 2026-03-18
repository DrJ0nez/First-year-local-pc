package dk.itu.group12.bornholm;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dk.itu.group12.bornholm.controller.MapController;
import dk.itu.group12.bornholm.model.OsmNode;
import dk.itu.group12.bornholm.model.OsmWay;
import dk.itu.group12.bornholm.view.DrawingApp;
import dk.itu.group12.bornholm.view.MapFunctions;
import dk.itu.group12.bornholm.view.SuperAffine;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import parser.osmParser;

public class App extends DrawingApp {

    private List<OsmWay> ways;
    private final MapFunctions mapFunctions = new MapFunctions();
    private final MapController mapController = new MapController(mapFunctions, this::redraw);

    @Override
    public void start(Stage stage) {
        osmParser parser = new osmParser("bornholm/bornholm.osm"); //Kan ændres til bornholm, samsø osv.
        parser.parse();
        this.ways = List.copyOf(parser.getOsmWayMap().values());

        List<Double> bb = parser.getBoundingBox();
        mapFunctions.reCenter(new double[]{bb.get(1), bb.get(0), bb.get(3), bb.get(2)}, getHEIGHT());

        //Opsætter selve imageview af kortet
        stage.setTitle("Group 12 Map");
        stage.setResizable(false);
        stage.setWidth(getWIDTH());
        stage.setHeight(getHEIGHT());
        stage.setScene(new Scene(new StackPane(this.imageView)));
        stage.show();

        mapController.initEvents(this.imageView);

        redraw();
    }

    private void redraw() {
        Graphics2D gc = getNewGraphicsContext();

        // 1. Hav-baggrund i skærmkoordinater (ingen transform endnu)
        gc.setColor(Color.decode("#aad3df"));
        gc.fillRect(0, 0, getWIDTH(), getHEIGHT());

        // 2. Sæt transform
        SuperAffine transform = mapFunctions.getTransform();
        gc.setTransform(transform);

        float strokeWidth = transform.getStrokeBaseWidth();

        // 3. Tegn kystlinje som ét samlet fyldt polygon
        Path2D coastline = stitchCoastline(ways);
        gc.setColor(Color.decode("#f0e9dd"));
        gc.fill(coastline);

        // 4. Tegn resten ovenpå
        for (OsmWay way : ways) {
            Map<String, String> tags = way.getTags();
            String highway = tags.get("highway");
            String natural = tags.get("natural");
            String landuse = tags.get("landuse");
            String building = tags.get("building");
            String waterway = tags.get("waterway");

            Color color = null;

            if ("coastline".equals(natural)) {
                // Allerede tegnet, skip
            } else if (highway != null) {
                color = Color.GRAY;
            } else if ("wood".equals(landuse) || "forest".equals(landuse)) {
                color = Color.decode("#aed1a0");
            } else if ("water".equals(natural) || "water".equals(landuse)) {
                color = Color.decode("#aad3df");
            } else if (building != null) {
                color = Color.decode("#d9b99b");
            } else if (waterway != null) {
                color = Color.decode("#aad3df");
            }

            if (color != null) {
                way.draw(gc, color, strokeWidth);
            }
        }

        render();
    }

    private Path2D stitchCoastline(List<OsmWay> ways) {
        List<List<OsmNode>> segments = new ArrayList<>();
        for (OsmWay way : ways) {
            if ("coastline".equals(way.getTags().get("natural")) && way.getNodes() != null) {
                segments.add(new ArrayList<>(way.getNodes()));
            }
        }

        if (segments.isEmpty()) return new Path2D.Double();

        // WIND_EVEN_ODD håndterer korrekt at øer ikke "annullerer" hinanden
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        while (!segments.isEmpty()) {
            // Start en ny ring med det første tilgængelige segment
            List<OsmNode> ring = new ArrayList<>(segments.remove(0));

            // Sy segmenter på indtil ringen er lukket eller ingen match findes
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

            // Tilføj denne ring som en subpath
            OsmNode first = ring.get(0);
            if (first == null) continue;
            path.moveTo(first.getLon() * 0.56, -first.getLat());

            for (int i = 1; i < ring.size(); i++) {
                OsmNode n = ring.get(i);
                if (n == null) {

                }
                path.lineTo(n.getLon() * 0.56, -n.getLat());
            }
            path.closePath(); // Luk denne ø og start klar til næste
        }

        return path;
    }
}