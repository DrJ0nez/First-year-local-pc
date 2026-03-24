package dk.itu.group12.bornholm.view;

import dk.itu.group12.bornholm.model.SeaLevelSimulation;

public class MapFunctions {
    private static final SuperAffine transform = new SuperAffine();
    private SeaLevelSimulation seaLevelSimulation;

     //Skill 1: Panning
     public void pan(double dx, double dy) {
     transform.prependTranslation(dx, dy);
     }


     //Skill 2: Zooming (Stretching)
    public void zoom(double factor, double x, double y) {
        transform.prependTranslation(-x, -y);
        transform.prependScale(factor, factor);
        transform.prependTranslation(x, y);
    }

     //Skill 3: The "Home" Button (Re-centering)

    public void reCenter(double[] bounds, double screenHeight) {
        // Calculate how much we need to stretch the map to fit the screen
        double islandHeight = bounds[3] - bounds[1];
        double scale = screenHeight / islandHeight;

        transform.reset();
        //Maybe these are not needed; Depends on the map.
        transform.prependTranslation(-0.56 * bounds[0], bounds[3]);

        transform.prependScale(scale, scale);
    }


    //Skill 4: Slider for SeaLevel
    public void setSeaLevelSimulation(SeaLevelSimulation seaLevelSimulation) {
        this.seaLevelSimulation = seaLevelSimulation;
    }

    public void updateSeaLevel(double meters) {
        if (seaLevelSimulation != null) {
            seaLevelSimulation.setSeaLevel(meters); // Calling your existing method!
        }
    }

    //Skill 5: Scalebar logic
    public static double getPixelsPerKM() {
         double currentScale = transform.getScaleX();
         double metersPerPixel = 1 / (currentScale / (111320 * 0.56));
        return 1000 / metersPerPixel;
    }



    public SuperAffine getTransform() {
        return transform;
    }
}