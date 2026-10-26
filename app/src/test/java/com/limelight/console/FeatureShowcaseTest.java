package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.console.FeatureShowcase.Feature;
import com.limelight.console.FeatureShowcase.Need;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class FeatureShowcaseTest {
    @Test public void visibleKeepsCatalogOrderAndDropsUnmetNeeds() {
        List<Feature> shown = FeatureShowcase.visible(EnumSet.of(Need.HOST_GAMES, Need.PAD_GYRO));
        assertEquals(Arrays.asList(Feature.GAMES, Feature.GYRO, Feature.QUALITY,
                Feature.FREEZE, Feature.AUTOPILOT, Feature.GAME_MODE), shown);
        // Without any host or pad only the cards every stream offers remain.
        assertEquals(Arrays.asList(Feature.QUALITY, Feature.FREEZE, Feature.AUTOPILOT),
                FeatureShowcase.visible(EnumSet.noneOf(Need.class)));
    }

    @Test public void everyCardExplainsItselfInThreeSteps() {
        for (Feature feature : Feature.values()) assertEquals(feature.id, 3, feature.steps.length);
    }

    @Test public void freshInstallAnnouncesNothing() {
        List<Feature> shown = FeatureShowcase.visible(EnumSet.allOf(Need.class));
        assertTrue(FeatureShowcase.unseen(null, shown, false).isEmpty());
    }

    @Test public void upgradeWithoutStoredSetAnnouncesOnlyNewCards() {
        List<Feature> shown = FeatureShowcase.visible(EnumSet.allOf(Need.class));
        List<Feature> fresh = FeatureShowcase.unseen(null, shown, true);
        assertTrue(fresh.contains(Feature.SUSPEND));
        assertFalse(fresh.contains(Feature.GAMES));
        assertFalse(fresh.contains(Feature.CHILDREN));
    }

    @Test public void laterUpdateAnnouncesOnlyCardsMissingFromTheStoredSet() {
        List<Feature> shown = FeatureShowcase.visible(EnumSet.allOf(Need.class));
        Set<String> seen = FeatureShowcase.markSeen(null, Arrays.asList(Feature.SUSPEND, Feature.GYRO));
        List<Feature> fresh = FeatureShowcase.unseen(seen, shown, true);
        assertFalse(fresh.contains(Feature.SUSPEND));
        assertTrue(fresh.contains(Feature.USB));
        assertTrue(fresh.contains(Feature.GAMES));
    }

    @Test public void cardsThisHostCannotShowStayUnseenUntilTheyAppear() {
        Set<String> seen = FeatureShowcase.markSeen(null,
                FeatureShowcase.visible(EnumSet.of(Need.PAD)));
        assertEquals(Arrays.asList(Feature.SUSPEND), FeatureShowcase.unseen(seen,
                FeatureShowcase.visible(EnumSet.of(Need.PAD, Need.HOST_SUSPEND)), true));
    }

    @Test public void allIdsCoversTheCatalogSoNothingIsAnnouncedLater() {
        assertTrue(FeatureShowcase.unseen(FeatureShowcase.allIds(),
                FeatureShowcase.visible(EnumSet.allOf(Need.class)), true).isEmpty());
    }
}
