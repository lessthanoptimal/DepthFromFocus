package peterabeles;

import boofcv.alg.misc.HistogramStatistics;
import boofcv.alg.misc.ImageStatistics;
import boofcv.core.image.ConvertImage;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.InterleavedU8;
import libwebcam.WebcamDriver;

import javax.swing.*;
import java.awt.image.BufferedImage;

/**
 * Opens the webcam and collects images. An algorithm is provided to select the best exposure level. You might
 * want to modify the code and specify a value of your choosing. When you run the app a window is opened showing
 * you what the webcam sees.
 *
 * For this to work your webcam must have manual controls for focus, gain, and exposure.
 *
 * @author Peter Abeles
 */
public class CollectImagesApp {

    WebcamDriver webcam;

    BufferedImage buffered;
    InterleavedU8 color = new InterleavedU8(1,1,1);
    GrayU8 gray = new GrayU8(1,1);
    GrayS16 laplacian = new GrayS16(1,1);
    ImagePanel gui;

    public CollectImagesApp(WebcamDriver webcam ) {
        this.webcam = webcam;

        // predeclare memory
        color.reshape(webcam.imageWidth(),webcam.imageHeight(),webcam.imageBands());
        gray.reshape(color.width,color.height);
        laplacian.reshape(color.width,color.height);
        buffered = new BufferedImage(color.width,color.height,BufferedImage.TYPE_INT_RGB);

        // open the GUI window
        gui = ShowImages.showWindow(buffered,"Webcam",true);
    }

    /**
     * Captures and saves all the data
     */
    public void process() {
        // Pick the exposure level with maximizes the image's edge intensity
        int exposure = selectExposure();
        webcam.setExposure(false,exposure);

        // Go through every exposure level and save the results
        saveImageAtEachFocusLevel();
    }

    /**
     * Collect images for 500ms then use the last one. This gives the device time to change its settings based
     * on your request
     */
    public void captureFrameBatch() {
        long endTime = System.currentTimeMillis() + 500;
        while( endTime > System.currentTimeMillis() ) {
            webcam.capture(color);
        }
        ConvertBufferedImage.convertTo(color,buffered,true);
        ConvertImage.average(color,gray);
        gui.repaint();
    }

    /**
     * Just pick a value for the gain.
     */
    public void setGainNominal() {
        int min = webcam.readGain(WebcamDriver.ValueType.MIN);
        int max = webcam.readGain(WebcamDriver.ValueType.MAX);

        webcam.setGain(false,max-min);
    }

    /**
     * Selects the exposure which maximizes the histogram's variance
     */
    public int selectExposure() {
        // Fix the other parameters
        webcam.setFocus(false,20);
        setGainNominal();
        // give the camera time to change settings
        captureFrameBatch();

        int min = webcam.readExposure(WebcamDriver.ValueType.MIN);
        int max = webcam.readExposure(WebcamDriver.ValueType.MAX);
        int step = webcam.readExposure(WebcamDriver.ValueType.STEP);

        int N = laplacian.width*laplacian.height;

        int histogram[] = new int[256];
        int optimalExposure = -1;
        double bestVariance = -1;
        for (int exposure = min; exposure <= max; exposure += step) {
            webcam.setExposure(false,exposure);

            captureFrameBatch();

            ImageStatistics.histogram(gray,0,histogram);
            double mean = HistogramStatistics.mean(histogram,256);
            double variance = HistogramStatistics.variance(histogram,mean,256);

            if( variance > bestVariance ) {
                bestVariance = variance;
                optimalExposure = exposure;
            }
            System.out.println("exposure "+exposure+"/"+max+"  variance="+variance);
        }
        System.out.println("    Selected "+optimalExposure);

        return optimalExposure;
    }

    /**
     * Goes through all possible focus values and saves the collected images to disk
     */
    public void saveImageAtEachFocusLevel() {
        int min = webcam.readFocus(WebcamDriver.ValueType.MIN);
        int max = webcam.readFocus(WebcamDriver.ValueType.MAX);
        int step = webcam.readFocus(WebcamDriver.ValueType.STEP);

        for (int focus = min; focus <= max; focus += step) {
            System.out.println("focus = "+focus+" / "+max);

            webcam.setFocus(false, focus);
            captureFrameBatch();

            UtilImageIO.saveImage(buffered,String.format("frame_%03d.png",focus));
        }
    }

    public static void main(String[] args) {
        WebcamDriver webcam = new WebcamDriver();

        if( !webcam.open(640,480) ) {
            throw new RuntimeException("Failed to open a webcam");
        }
        // close the device properly even if some jerk hits control-c
        Runtime.getRuntime().addShutdownHook(new Thread(() -> webcam.close()));


        System.out.println("Opened "+webcam.getDevice()+"  "+webcam.imageWidth()+"x"+webcam.imageHeight());

        // See if it meets our requirements
        boolean manualExposure = 1==webcam.readExposure(WebcamDriver.ValueType.MANUAL);
        boolean manualGain = 1==webcam.readGain(WebcamDriver.ValueType.MANUAL);
        boolean manualFocus = 1==webcam.readFocus(WebcamDriver.ValueType.MANUAL);

        if( !manualExposure || !manualGain || !manualFocus ) {
            throw new RuntimeException("Lacking complete manual controls");
        }

        // set up the GUI in the GUI thread
        SwingUtilities.invokeLater(() -> {
            CollectImagesApp app = new CollectImagesApp(webcam);
            // launch the image collection in its own thread as to not freeze the GUI
            new Thread(() -> app.process()).start();
        });
    }

}
