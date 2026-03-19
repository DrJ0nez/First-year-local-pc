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

    // Identity transform til at nulstille Graphics2D mellem frames
    private static final AffineTransform IDENTITY = new AffineTransform();

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

    // Highways med navne – til vejnavne-rendering
    private List<OsmWay> namedHighways;

    // Cached stroke – genbruges så længe strokeWidth ikke ændrer sig
    private float lastStrokeWidth = -1;
    private BasicStroke cachedStroke;

    // Font og stroke til vejnavne – oprettes én gang
    private static final Font ROAD_NAME_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Color ROAD_NAME_COLOR = new Color(80, 80, 80);
    private static final BasicStroke HALO_STROKE = new BasicStroke(3f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    @Override
    public void start(Stage stage) {
        osmParser parser = new osmParser("bornholm/bornholm.osm"); //Kan ændres til bornholm, samsø osv.
        parser.parse();
        this.ways = List.copyOf(parser.getOsmWayMap().values());

        // Byg coastline én gang – den ændrer sig aldrig
        cachedCoastline = stitchCoastline(ways);

        // Kategoriser ways én gang ved opstart i stedet for at tjekke tags hver frame
        highways      = new ArrayList<>();
        namedHighways = new ArrayList<>();
        forests       = new ArrayList<>();
        waters        = new ArrayList<>();
        buildings     = new ArrayList<>();
        waterways     = new ArrayList<>();

        for (OsmWay way : ways) {
            Map<String, String> tags = way.getTags();
            String natural  = tags.get("natural");
            String landuse  = tags.get("landuse");

            if ("coastline".equals(natural)) continue; // håndteres separat

            if (tags.get("highway") != null) {
                highways.add(way);
                if (tags.get("name") != null) {
                    namedHighways.add(way);
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

        // 1. Hav-baggrund i skærmkoordinater – nulstil transform først
        //    (cached Graphics2D beholder transform fra forrige frame)
        gc.setTransform(IDENTITY);
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

        // 4. Beregn synligt viewport i map-koordinater via inverse transform
        Point2D topLeft     = transform.inverseTransform(0, 0);
        Point2D bottomRight = transform.inverseTransform(getWIDTH(), getHEIGHT());
        double vpMinX = Math.min(topLeft.getX(), bottomRight.getX());
        double vpMinY = Math.min(topLeft.getY(), bottomRight.getY());
        double vpMaxX = Math.max(topLeft.getX(), bottomRight.getX());
        double vpMaxY = Math.max(topLeft.getY(), bottomRight.getY());

        // 5. Level of Detail: beregn zoom-niveau for at filtrere detaljer
        double zoomLevel = Math.abs(transform.getScaleX());

        // 6. Tegn kun ways der er synlige i viewport
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

        // Bygninger og highways vises kun når man er zoomet tilstrækkeligt ind
        if (zoomLevel > 3000) {
            for (OsmWay way : highways) {
                if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                    way.draw(gc, Color.GRAY, strokeWidth, cachedStroke);
            }
        }
        if (zoomLevel > 8000) {
            for (OsmWay way : buildings) {
                if (way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY))
                    way.draw(gc, COLOR_BUILDING, strokeWidth, cachedStroke);
            }
        }

        // 7. Tegn vejnavne som sidste lag – i skærmkoordinater for skarp tekst
        if (zoomLevel > 8000) {
            drawRoadNames(gc, transform, vpMinX, vpMinY, vpMaxX, vpMaxY);
        }

        render();
    }

    /** Tegner vejnavne i skærmkoordinater med hvid halo for læsbarhed */
    private void drawRoadNames(Graphics2D gc, SuperAffine transform,
                               double vpMinX, double vpMinY, double vpMaxX, double vpMaxY) {
        // Skift til skærmkoordinater for skarp tekst
        gc.setTransform(IDENTITY);
        gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        gc.setFont(ROAD_NAME_FONT);

        FontRenderContext frc = gc.getFontRenderContext();

        for (OsmWay way : namedHighways) {
            if (!way.isVisible(vpMinX, vpMinY, vpMaxX, vpMaxY)) continue;

            List<OsmNode> nodes = way.getNodes();
            if (nodes == null || nodes.size() < 2) continue;

            String name = way.getTags().get("name");

            // Find midtpunktet af vejen for placering
            int midIdx = nodes.size() / 2;
            OsmNode midNode = nodes.get(midIdx);
            OsmNode prevNode = nodes.get(Math.max(0, midIdx - 1));
            if (midNode == null || prevNode == null) continue;

            // Konverter midtpunkt til skærmkoordinater
            double mapX = midNode.getLon() * 0.56;
            double mapY = -midNode.getLat();
            double[] screenPt = new double[2];
            transform.transform(new double[]{mapX, mapY}, 0, screenPt, 0, 1);
            double sx = screenPt[0];
            double sy = screenPt[1];

            // Spring over hvis teksten er uden for skærmen
            if (sx < -100 || sx > getWIDTH() + 100 || sy < -20 || sy > getHEIGHT() + 20) continue;

            // Beregn vejens retning for tekstrotation
            double prevMapX = prevNode.getLon() * 0.56;
            double prevMapY = -prevNode.getLat();
            double[] prevScreenPt = new double[2];
            transform.transform(new double[]{prevMapX, prevMapY}, 0, prevScreenPt, 0, 1);

            double angle = Math.atan2(screenPt[1] - prevScreenPt[1], screenPt[0] - prevScreenPt[0]);

            // Sørg for at tekst altid er læsbar (ikke på hovedet)
            if (angle > Math.PI / 2) angle -= Math.PI;
            if (angle < -Math.PI / 2) angle += Math.PI;

            // Tegn med GlyphVector for at kunne lave hvid halo
            GlyphVector gv = ROAD_NAME_FONT.createGlyphVector(frc, name);

            AffineTransform textTransform = new AffineTransform();
            textTransform.translate(sx, sy);
            textTransform.rotate(angle);
            // Centrer teksten på midtpunktet
            double textWidth = gv.getLogicalBounds().getWidth();
            textTransform.translate(-textWidth / 2, 0);

            gc.setTransform(textTransform);

            // Hvid halo – tegn outline af bogstaverne
            gc.setColor(Color.WHITE);
            gc.setStroke(HALO_STROKE);
            gc.draw(gv.getOutline());

            // Selve teksten ovenpå
            gc.setColor(ROAD_NAME_COLOR);
            gc.fill(gv.getOutline());

            gc.setTransform(IDENTITY);
        }
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