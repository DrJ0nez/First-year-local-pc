package dk.itu.group12.bornholm.view;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;

import javafx.application.Application;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

public abstract class DrawingApp extends Application {

    private static final int WIDTH = 800, HEIGHT = 800;

    public static int getWIDTH() { return WIDTH; }
    public static int getHEIGHT() { return HEIGHT; }

    private final PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(
            WIDTH, HEIGHT,
            IntBuffer.allocate(WIDTH * HEIGHT),
            PixelFormat.getIntArgbPreInstance()
    );
    private final BufferedImage bufferedImage = new BufferedImage(
            WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB
    );
    // WritableImage genbruges – oprettes kun én gang
    private final WritableImage writableImage = new WritableImage(pixelBuffer);
    protected final ImageView imageView = new ImageView(writableImage);

    // Cache Graphics2D konteksten i stedet for at oprette ny hver frame
    private Graphics2D cachedGraphics;

    public final Graphics2D getNewGraphicsContext() {
        if (cachedGraphics == null) {
            cachedGraphics = bufferedImage.createGraphics();
        }
        return cachedGraphics;
    }

    public final void render() {
        int[] pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, pixelBuffer.getBuffer().array(), 0, pixels.length);
        // Fortæl PixelBuffer at indholdet er ændret – ingen ny WritableImage nødvendig
        pixelBuffer.updateBuffer(pb -> null);
    }
}