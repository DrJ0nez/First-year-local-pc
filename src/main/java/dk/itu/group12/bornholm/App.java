package dk.itu.group12.bornholm;

import java.awt.BasicStroke;
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

    // Cached coastline – bygges kun én gang ved opstart
    private Path2D cachedCoastline;

    // Cached farver – undgår Color.decode() i render-loopet
    private static final Color COLOR_SEA      = Color.decode("#aad3df");
    private static final Color COLOR_LAND     = Color.decode("#f0e9dd");
    private static final Color COLOR_FOREST   = Color.decode("#aed1a0");
    private static final Color COLOR_BUILDING = Color.decode("#d9b99b");

    // Forud-kategoriserede lister – undgår tag-opslag i render-loopet
    private List<OsmWay> highways;
    private List<OsmWay> forests;
    private List<OsmWay> waters;
    private List<OsmWay> buildings;
    private List<OsmWay> waterways;

    // Cached stroke – genbruges så længe strokeWidth ikke ændrer sig
    private float lastStrokeWidth = -1;
    private BasicStroke cachedStroke;

    @Override
    public void start(Stage stage) {
        osmParser parser = new osmParser("bornholm/bornholm.osm"); //Kan ændres til bornholm, samsø osv.
        parser.parse();
        this.ways = List.copyOf(parser.getOsmWayMap().values());

        // Byg coastline én gang – den ændrer sig aldrig
        cachedCoastline = stitchCoastline(ways);

        // Kategoriser ways én gang ved opstart i stedet for at tjekke tags hver frame
        highways  = new ArrayList<>();
        forests   = new ArrayList<>();
        waters    = new ArrayList<>();
        buildings = new ArrayList<>();
        waterways = new ArrayList<>();

        for (OsmWay way : ways) {
            Map<String, String> tags = way.getTags();
            String natural  = tags.get("natural");
            String landuse  = tags.get("landuse");

            if ("coastline".equals(natural)) continue; // håndteres separat

            if (tags.get("highway") != null) {
                highways.add(way);
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
        gc.setColor(COLOR_SEA);
        gc.fillRect(0, 0, getWIDTH(), getHEIGHT());

        // 2. Sæt transform
        SuperAffine transform = mapFunctions.getTransform();
        gc.setTransform(transform);

        float strokeWidth = transform.getStrokeBaseWidth();

        // Cache BasicStroke – ny oprettes kun når strokeWidth faktisk ændrer sig
        if (strokeWidth != lastStrokeWidth) {
            cachedStroke = new BasicStroke(strokeWidth);
            lastStrokeWidth = strokeWidth;
        }

        // 3. Tegn kystlinje (cached – ingen stitching per frame)
        gc.setColor(COLOR_LAND);
        gc.fill(cachedCoastline);

        // 4. Level of Detail: beregn zoom-niveau for at filtrere detaljer
        //    getScaleX() giver pixels per map-enhed. Højere = mere zoomet ind.
        double zoomLevel = Math.abs(transform.getScaleX());

        // 5. Tegn kategoriserede ways – ingen tag-opslag i loopet
        for (OsmWay way : forests) {
            way.draw(gc, COLOR_FOREST, strokeWidth, cachedStroke);
        }
        for (OsmWay way : waters) {
            way.draw(gc, COLOR_SEA, strokeWidth, cachedStroke);
        }
        for (OsmWay way : waterways) {
            way.draw(gc, COLOR_SEA, strokeWidth, cachedStroke);
        }

        // Bygninger og highways vises kun når man er zoomet tilstrækkeligt ind
        // Tærskelværdi: ved fuldt udzoomet Bornholm er scale ca. ~1400-1500
        if (zoomLevel > 3000) {
            for (OsmWay way : highways) {
                way.draw(gc, Color.GRAY, strokeWidth, cachedStroke);
            }
        }
        if (zoomLevel > 8000) {
            for (OsmWay way : buildings) {
                way.draw(gc, COLOR_BUILDING, strokeWidth, cachedStroke);
            }
        }

        render();
    }

    /** Bygger coastline Path2D – kaldes kun én gang ved opstart */
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
                if (n == null) continue;
                path.lineTo(n.getLon() * 0.56, -n.getLat());
            }
            path.closePath(); // Luk denne ø og start klar til næste
        }

        return path;
    }
}