package dk.itu.group12.bornholm.controller;

import dk.itu.group12.bornholm.view.MapFunctions;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.Node;
import javafx.scene.text.Text;

public class MapController {
    private final MapFunctions mapFunctions;
    private final Runnable redrawRequest;

    private double lastMouseX;
    private double lastMouseY;

    private Slider zoomSlider;
    private Text zoomLabel;

    public MapController(MapFunctions mapFunctions, Runnable redrawRequest) {
        this.mapFunctions = mapFunctions;
        this.redrawRequest = redrawRequest;


    }

    /**
     * Call this once to "hook up" the mouse to the screen.
     */
    public void initEvents(Node target) {
        target.setOnMousePressed(this::handleMousePressed);
        target.setOnMouseDragged(this::handleMouseDragged);
        target.setOnScroll(this::handleScroll);
    }

    private void handleMousePressed(MouseEvent event) {
        this.lastMouseX = event.getX();
        this.lastMouseY = event.getY();
    }

    private void handleMouseDragged(MouseEvent event) {
        double dx = event.getX() - this.lastMouseX;
        double dy = event.getY() - this.lastMouseY;

        mapFunctions.pan(dx, dy);

        this.lastMouseX = event.getX();
        this.lastMouseY = event.getY();

        redrawRequest.run();
    }

    private void handleScroll(ScrollEvent event) {
        double delta = event.getDeltaY();
        if (delta == 0) return; // Ignore "ghost" events

        double factor = (delta > 0) ? 1.05 : 0.95;

        double currentValue = zoomSlider.getValue();
        double newValue = currentValue * factor;

        newValue = Math.max(zoomSlider.getMin(), Math.min(zoomSlider.getMax(), newValue));

        double actualFactor = newValue / currentValue;

        mapFunctions.zoom(actualFactor, event.getX(), event.getY());
        zoomSlider.setValue(newValue);
        zoomLabel.setText(String.format("Zoom: %.0f%%", newValue * 100));

        redrawRequest.run();
    }


    public void sliders(Slider zoomSlider, Text zoomLabel, Slider seaLevelSlider, Text seaLevelLabel) {
        this.zoomSlider = zoomSlider;
        this.zoomLabel = zoomLabel;

        //Zoom slider
        zoomSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(zoomSlider.isValueChanging() || !zoomSlider.isFocused()) {
                 double factor = newValue.doubleValue() / oldValue.doubleValue();
                 mapFunctions.zoom(factor, 640, 360);

                 //Zoom label
                 zoomLabel.setText(String.format("Zoom: %.0f%% ",  newValue.doubleValue() * 100));

                 redrawRequest.run();
            }
           
        });
        // seaLevelSlider
        seaLevelSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            mapFunctions.updateSeaLevel(newValue.doubleValue());

            //Sea label
            seaLevelLabel.setText(String.format("Sea Level: %.1fm", newValue.doubleValue()));

            redrawRequest.run();
        });

    }
}