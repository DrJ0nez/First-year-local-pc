package dk.itu.group12.bornholm.model;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class HeightCurve {

    long id;
    double height;
    List<Coordinate> coords;
    List<HeightCurve> children;
    boolean submerged;


    public Path2D getBoundaryPath() {
        Path2D.Double boundary = new Path2D.Double();
        if(!coords.isEmpty()) {
            for(int i = 0; i < coords.size(); i++) {
                Coordinate point = coords.get(i);
                if(i == 0) {
                    boundary.moveTo(point.getLon(), point.getLat());
                }
                else {
                    boundary.lineTo(point.getLon(), point.getLat());
                }
            }
        }
        return boundary;
    }


    public Path2D getRegionPath() {
        Path2D region = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        region.append(getBoundaryPath(), false);

        for (HeightCurve child : children) {
            region.append(child.getBoundaryPath(), false);
        }
        return region;
    }


    public Color getFillColor(double seaLevel) {
        // Semi-transparent so overlapping regions are visually debuggable.
        double elevation = height - seaLevel;

        if(elevation < 0 && submerged) return Color.decode("#2b8cbe");

        else if(elevation < 0 && !submerged) return Color.decode("#ffffcc");

        else if(elevation >= 0 && elevation < 2.5) return Color.decode("#c2e699");

        else if(elevation >= 2.5 && elevation < 5) return Color.decode("#78c679");

        else if(elevation >= 5 && elevation < 7.5) return Color.decode("#31a354");

        else return Color.decode("#006837");
    }


    public void resetSubmerged() {
        this.submerged = false;
        for(HeightCurve child : children) {
            child.resetSubmerged();
        }
    }


    public void submerge(double seaLevel) {
        if (seaLevel > height) {
            submerged = true;
            for (HeightCurve child : children) {
                child.submerge(seaLevel);
            }
        }
    }

    // ------------------------------------
    // Don't edit anything below here
    // ------------------------------------
    public HeightCurve() {
        this(0L, 0.0, new ArrayList<>(), new ArrayList<>());
    }

    public HeightCurve(long id, double height, List<Coordinate> coords) {
        this(id, height, coords, new ArrayList<>());
    }

    public HeightCurve(long id, double height, List<Coordinate> coords, List<HeightCurve> children) {
        this.id = id;
        this.height = height;
        this.coords = coords != null ? coords : new ArrayList<>();
        this.children = children != null ? children : new ArrayList<>();
        this.submerged = false;
    }

    public long getId() {
        return id;
    }

    public double getHeight() {
        return height;
    }

    public List<Coordinate> getCoords() {
        return coords;
    }

    public List<HeightCurve> getChildren() {
        return children;
    }

    public boolean isSubmerged() {
        return submerged;
    }
}
