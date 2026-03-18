package dk.itu.group12.bornholm.model;

import java.util.List;

public class SeaLevelSimulation {
    private double seaLevel = 0.0;
    private final List<HeightCurve> allCurves;

    public SeaLevelSimulation(List<HeightCurve> allCurves, double seaLevel) {
        this.allCurves = allCurves;
        this.seaLevel = seaLevel;
        updateSimulation();
    }

    public void setSeaLevel(double seaLevel) {
        this.seaLevel = seaLevel;
        updateSimulation();
    }

    private void updateSimulation() {
        for (HeightCurve curve : allCurves) {
            curve.resetSubmerged();
            curve.submerge(seaLevel);
        }
    }

        //Helper & getter methods
    public double getCurrentSeaLevel() {
        return seaLevel;
    }
}