package com.limelight.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LoadingArtworkPolicyTest {
    @Test
    public void acceptsLandscapeHdArtwork() {
        assertTrue(LoadingArtworkPolicy.canUseAsSplash(1004, 550));
        assertTrue(LoadingArtworkPolicy.canUseAsSplash(1004, 626));
        assertTrue(LoadingArtworkPolicy.canUseAsSplash(1280, 720));
        assertTrue(LoadingArtworkPolicy.canUseAsSplash(1920, 1080));
    }

    @Test
    public void rejectsThumbnailsButAllowsPortraitSplash() {
        assertFalse(LoadingArtworkPolicy.canUseAsSplash(639, 360));
        assertFalse(LoadingArtworkPolicy.canUseAsSplash(640, 359));
        assertTrue(LoadingArtworkPolicy.canUseAsSplash(800, 1200));
    }

    @Test public void fillsNearScreenAspectWithoutStretchingAndContainsPortraits() {
        assertEquals(1.5f, LoadingArtworkPolicy.scale(1280, 720, 1920, 1080), .001f);
        assertEquals(1920f / 1004, LoadingArtworkPolicy.scale(1004, 626, 1920, 1080), .001f);
        assertEquals(.9f, LoadingArtworkPolicy.scale(800, 1200, 1920, 1080), .001f);
        assertEquals(1.44f, LoadingArtworkPolicy.scale(1000, 750, 1920, 1080), .001f);
        assertEquals(2f, LoadingArtworkPolicy.scale(640, 360, 1920, 1080), .001f);
        assertEquals(1f, LoadingArtworkPolicy.scale(320, 180, 1920, 1080), .001f);
    }

    @Test public void downsamplingKeepsEnoughPixelsForTheDisplay() {
        assertEquals(1, LoadingArtworkPolicy.sampleSize(2560, 1440, 1920));
        assertEquals(2, LoadingArtworkPolicy.sampleSize(3840, 2160, 1920));
    }

    @Test public void tileFillAllowsSmallCropButContainsVeryDifferentArtwork() {
        assertTrue(LoadingArtworkPolicy.canFillWithModestCrop(600, 900, 92, 123));
        assertTrue(LoadingArtworkPolicy.canFillWithModestCrop(1200, 1600, 92, 123));
        assertTrue(LoadingArtworkPolicy.canFillWithModestCrop(1024, 1152, 92, 123));
        assertFalse(LoadingArtworkPolicy.canFillWithModestCrop(1920, 1080, 92, 123));
    }

    @Test public void lightArtworkUnderTheCardGetsExtraShadeDarkArtNone() {
        int[] dark = new int[100];
        java.util.Arrays.fill(dark, 0xFF1A1C22);
        assertEquals(0f, LoadingArtworkPolicy.legibilityBoost(dark), .001f);
        int[] white = new int[100];
        java.util.Arrays.fill(white, 0xFFF4F6FA);
        assertEquals(1f, LoadingArtworkPolicy.legibilityBoost(white), .001f);
        // A light patch over a fifth of the area (a sky behind the title) counts in full.
        int[] patch = dark.clone();
        java.util.Arrays.fill(patch, 75, 100, 0xFFFFFFFF);
        assertEquals(1f, LoadingArtworkPolicy.legibilityBoost(patch), .001f);
        int[] speck = dark.clone();
        java.util.Arrays.fill(speck, 90, 100, 0xFFFFFFFF);
        assertEquals(0f, LoadingArtworkPolicy.legibilityBoost(speck), .001f);
        assertEquals(0f, LoadingArtworkPolicy.legibilityBoost(new int[0]), .001f);
    }
}
