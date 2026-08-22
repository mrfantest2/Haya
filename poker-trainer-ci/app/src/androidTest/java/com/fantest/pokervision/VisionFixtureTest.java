package com.fantest.pokervision;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VisionFixtureTest {
    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV must initialize", OpenCVLoader.initDebug());
    }

    @Test public void detectorFindsSeparatedPokerCardRectangles() {
        Mat frame = Mat.zeros(720, 1280, CvType.CV_8UC1);
        Imgproc.rectangle(frame, new Point(100, 100), new Point(310, 400), new Scalar(255), -1);
        Imgproc.rectangle(frame, new Point(430, 110), new Point(640, 410), new Scalar(255), -1);
        CardDetector detector = new CardDetector();
        List<ScanModels.Quad> quads = detector.detect(frame);
        assertEquals(2, quads.size());
        frame.release();
    }

    @Test public void normalizerProducesFixedCardAndIndexSizes() {
        Mat frame = Mat.zeros(720, 1280, CvType.CV_8UC1);
        Imgproc.rectangle(frame, new Point(100, 100), new Point(310, 400), new Scalar(255), -1);
        CardNormalizer normalizer = new CardNormalizer();
        CardNormalizer.Result r = normalizer.normalize(frame,
                ScanModels.Quad.fromBounds(100, 100, 310, 400));
        assertEquals(VisionConstants.CARD_WIDTH, r.card.cols());
        assertEquals(VisionConstants.CARD_HEIGHT, r.card.rows());
        assertEquals(VisionConstants.INDEX_WIDTH, r.index.cols());
        assertEquals(VisionConstants.INDEX_HEIGHT, r.index.rows());
        r.release();
        frame.release();
    }

    @Test public void generatedTemplateFamilyRecognizesCanonicalAceSpades() {
        TemplateStore store = new TemplateStore();
        Mat card = store.syntheticCard(14, 0);
        Mat index = card.submat(0, VisionConstants.INDEX_HEIGHT, 0, VisionConstants.INDEX_WIDTH).clone();
        SymbolPrediction<Integer> rank = new RankRecognizer(store).recognize(index);
        SymbolPrediction<Integer> suit = new SuitRecognizer(store).recognize(index);
        assertNotNull(rank);
        assertNotNull(suit);
        assertEquals(Integer.valueOf(14), rank.value);
        assertEquals(Integer.valueOf(0), suit.value);
        index.release();
        card.release();
        store.release();
    }
}
