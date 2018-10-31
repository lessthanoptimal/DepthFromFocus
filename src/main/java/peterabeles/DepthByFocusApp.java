package peterabeles;

import boofcv.alg.distort.radtan.LensDistortionRadialTangential;
import boofcv.alg.feature.detect.edge.GGradientToEdgeFeatures;
import boofcv.alg.filter.derivative.DerivativeType;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.core.image.border.BorderType;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.image.ShowImages;
import boofcv.gui.image.VisualizeImageData;
import boofcv.io.UtilIO;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import boofcv.visualize.PointCloudViewer;
import boofcv.visualize.VisualizeData;
import georegression.struct.point.Point2D_F64;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Loads previously saved images and attempts to extract the 3D location of each pixels. The algorithm used here
 * is what I pulled out of my ass without reading anything on the subject. It might not be all that great.
 *
 * Algorithm for Picking Focus:
 *
 * 1) For each pixel in the image, compute the edge intensity at every focus value.
 * 2) Pick the focus value with the largest edge intensity as the best focus for that pixel
 * 3) Discard pixels with an edge intensity which is too small or do not have an intensity which is a significant peak
 * in focus space.
 *
 * @author Peter Abeles
 */
public class DepthByFocusApp {
    CameraPinholeRadial intrinsic;
    List<String> images;

    public static float MIN_EDGE_INTENSITY = 30;
    public static float MIN_PEAK = 20;


    BufferedImage buffered;
    GrayU8 gray = new GrayU8(1,1);
    GrayS16 derivX = new GrayS16(1,1);
    GrayS16 derivY = new GrayS16(1,1);
    GrayF32 intensity = new GrayF32(1,1);
    GrayF32 bestFocus = new GrayF32(1,1);

    Planar<GrayF32> edges = new Planar<>(GrayF32.class,1,1,1);

    public DepthByFocusApp( List<String> images , CameraPinholeRadial intrinsic ) {

        this.intrinsic = intrinsic;
        this.images = images;

        buffered = UtilImageIO.loadImage(images.get(images.size()/2));

        gray.reshape(buffered.getWidth(),buffered.getHeight());
        derivX.reshape(gray.width,gray.height);
        derivY.reshape(gray.width,gray.height);
        intensity.reshape(gray.width,gray.height);
        bestFocus.reshape(gray.width,gray.height);

        edges.reshape(gray.width,gray.height,images.size());
    }

    /**
     * Loads images, computes depth, displays point cloud and other images.
     */
    public void process() {

        computeEdgeIntensityAcrossFocus(images);

        visualizeIntensityVsFocus();

        selectBestFocus();

        ShowImages.showWindow(buffered,"Input",true);
        ShowImages.showWindow(bestFocus,"Focus",true);

        computeCloud();
    }

    /**
     * For every image at each focus, compute the edge intensity
     * @param images Paths to input images
     */
    public void computeEdgeIntensityAcrossFocus( List<String> images ) {

        for( int i = 0; i < images.size(); i++ ) {
            String path = images.get(i);
            gray = UtilImageIO.loadImage(path,GrayU8.class);

            GrayF32 edgeFocus = edges.getBand(i);

            GImageDerivativeOps.gradient(DerivativeType.SOBEL,gray,derivX,derivY, BorderType.EXTENDED);

            GGradientToEdgeFeatures.intensityE(derivX, derivY, edgeFocus);
        }
    }

    /**
     * Show intensity images in a list to help us understand the problem
     */
    public void visualizeIntensityVsFocus() {
        ListDisplayPanel panel = new ListDisplayPanel();
        for (int i = 0; i < edges.getNumBands(); i++) {
            GrayF32 edge = edges.getBand(i);

            BufferedImage buff = VisualizeImageData.grayMagnitude(edge,null,500);
            panel.addImage(buff,new File(images.get(i)).getName());
        }
        ShowImages.showWindow(panel,"Intensity vs Focus",true);
    }

    /**
     * Selects best focus for each pixel and removes pixels which fail sanity checks
     */
    public void selectBestFocus() {
        int numBands = edges.getNumBands();

        for (int y = 0, idx = 0; y < edges.height; y++) {
            for (int x = 0; x < edges.width; x++, idx++ ) {
                int bestBand = -1;
                float bestIntensity = 0;

                for (int band = 0; band < numBands; band++) {
                    float v = edges.bands[band].data[idx];
                    if( v > bestIntensity ) {
                        bestIntensity = v;
                        bestBand = band;
                    }
                }

                // only trust strong edges
                if( bestIntensity < MIN_EDGE_INTENSITY || bestBand <= 0 || bestBand >= numBands-1 ) {
                    bestFocus.data[idx] = 0;
                } else {

                    // the pixel needs to be a strong local minimum
                    float v0 = edges.bands[bestBand-1].data[idx];
                    float v2 = edges.bands[bestBand+1].data[idx];

                    if( v0+MIN_PEAK < bestIntensity && v2+MIN_PEAK < bestIntensity ) {
                        bestFocus.data[idx] = bestBand;
                    } else {
                        bestFocus.data[idx] = 0;
                    }
                }
            }
        }
    }

    /**
     * Go from pixel + best focus into a point cloud. Focus is assumed to be one over the depth.
     */
    public void computeCloud() {
        Point2Transform2_F64 pixelToNorm = new LensDistortionRadialTangential(intrinsic).undistort_F64(true,false);
        Point2D_F64 n = new Point2D_F64();

        PointCloudViewer pcv = VisualizeData.createPointCloudViewer();
        pcv.setTranslationStep(0.05);


        for( int row = 0; row < bestFocus.height; row++ ) {
            for( int col = 0; col < bestFocus.width; col++ ) {
                double d = bestFocus.unsafe_get(col,row)/images.size();
                if( d <= 0 )
                    continue;

                pixelToNorm.compute(col,row,n);

                double z = 1.0/d;
                double x = n.x*z;
                double y = n.y*z;

                pcv.addPoint(x,y,z,buffered.getRGB(col,row));
            }
        }

        pcv.setCameraHFov(PerspectiveOps.computeHFov(intrinsic));
        JComponent viewer = pcv.getComponent();
        viewer.setPreferredSize(new Dimension(600,600*intrinsic.height/intrinsic.width));
        ShowImages.showWindow(pcv.getComponent(),"Point Cloud",true);
    }

    public static void main(String[] args) {
        List<String> images = UtilIO.listByPrefix("data","frame");
        Collections.sort(images);

        CameraPinholeRadial intrinsic = CalibrationIO.load(new File("data/intrinsic.yaml"));

        DepthByFocusApp app = new DepthByFocusApp(images,intrinsic);
        app.process();

    }
}
