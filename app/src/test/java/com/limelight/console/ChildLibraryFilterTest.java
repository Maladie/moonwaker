package com.limelight.console;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Mockup 23g: the parent library as one child sees it, and the editor's narrowed groups;
 * mockup 23d: the children's line under "Share game with child".
 */
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class ChildLibraryFilterTest {
    private static final String KEY = ChildLibraryFilter.key("host", "parent");
    private final PlayniteLibraryGame hades = game("steam:1145360", rating("T", 13));
    private final PlayniteLibraryGame knight = game("Steam:367520", rating("E10+", 10));
    private final PlayniteLibraryGame stardew = game("steam:413150", rating("E", 6));
    private final PlayniteLibraryGame unrated = game("epic:Fortnite", null);
    private final List<PlayniteLibraryGame> library =
            Arrays.asList(hades, knight, stardew, unrated);
    private final ChildLibraryFilter.Child ola = new ChildLibraryFilter.Child("ola", "Ola",
            "12", Collections.singleton("steam:1145360"), Collections.singleton("steam:367520"));

    @Test public void eachGroupShowsItsGamesInLibraryOrder() {
        assertEquals(Arrays.asList(hades, stardew), select(ChildLibraryFilter.Group.VISIBLE));
        assertEquals(Collections.singletonList(hades), select(ChildLibraryFilter.Group.GRANTED));
        assertEquals(Collections.singletonList(knight), select(ChildLibraryFilter.Group.BLOCKED));
        assertEquals(Collections.singletonList(unrated),
                select(ChildLibraryFilter.Group.UNRATED));
    }

    @Test public void childrenAreUnknownUntilLoadedAndAChoiceJoinsThem() {
        ChildLibraryFilter filter = new ChildLibraryFilter();
        assertNull(filter.children(KEY));
        filter.select(KEY, ola, null);
        assertEquals(ChildLibraryFilter.Group.VISIBLE, filter.selection(KEY).group);
        assertNull(filter.selection(ChildLibraryFilter.key("host", "other")));

        ChildLibraryFilter.Child kuba = child("kuba", "16");
        filter.remember(KEY, filter.begin(KEY), Arrays.asList(kuba, child("ola", "7")));
        // The choice follows its child's reloaded values.
        assertEquals("7", filter.selection(KEY).child.ageLimit);
        filter.select(KEY, ola, ChildLibraryFilter.Group.GRANTED);
        assertEquals(Arrays.asList(kuba, ola), filter.children(KEY));
        filter.remember(KEY, filter.begin(KEY), Collections.singletonList(kuba));
        assertNull(filter.selection(KEY));
        filter.select(KEY, kuba, null);
        filter.select(KEY, null, null);
        assertNull(filter.selection(KEY));
    }

    @Test public void aFailedReloadDropsTheListsAndTheChoice() {
        ChildLibraryFilter filter = new ChildLibraryFilter();
        filter.remember(KEY, filter.begin(KEY), Arrays.asList(ola, child("kuba", "16")));
        filter.select(KEY, ola, ChildLibraryFilter.Group.GRANTED);
        filter.forget(KEY, filter.begin(KEY));
        assertNull(filter.children(KEY));
        assertNull(filter.selection(KEY));
    }

    @Test public void onlyTheLatestLoadOfAKeyLands() {
        ChildLibraryFilter filter = new ChildLibraryFilter();
        ChildLibraryFilter.Child kuba = child("kuba", "16");
        int older = filter.begin(KEY);
        int newer = filter.begin(KEY);
        int otherKey = filter.begin(ChildLibraryFilter.key("host", "other"));
        assertTrue(filter.remember(KEY, newer, Arrays.asList(ola, kuba)));
        filter.select(KEY, ola, null);
        // The older load lands last: neither its list nor its failure replaces the newer one.
        assertFalse(filter.remember(KEY, older, Collections.singletonList(kuba)));
        assertFalse(filter.forget(KEY, older));
        assertEquals(Arrays.asList(ola, kuba), filter.children(KEY));
        assertEquals(ola, filter.selection(KEY).child);
        // Each key counts its own loads.
        assertTrue(filter.remember(ChildLibraryFilter.key("host", "other"), otherKey,
                Collections.singletonList(kuba)));
        assertTrue(filter.forget(KEY, newer));
        assertNull(filter.children(KEY));
    }

    @Test @Config(qualifiers = "pl")
    public void theSharingRowSumsUpEachChildByTheSameRule() {
        ChildLibraryFilter.Child kuba = new ChildLibraryFilter.Child("kuba", "Kuba", "16",
                Collections.emptySet(), Collections.emptySet());
        ChildLibraryFilter.Child maja = new ChildLibraryFilter.Child("maja", "Maja", "7",
                Collections.emptySet(), Collections.emptySet());
        ChildLibraryFilter.Child olaAll = new ChildLibraryFilter.Child("ola", "Ola", "all",
                Collections.emptySet(), Collections.emptySet());
        // Mockup 23d: Hades II (T, 13) is in Ola's and Kuba's categories, above Maja's 7+.
        assertEquals("Ola, Kuba z kategorii · Maja nie",
                summary(Arrays.asList(olaAll, kuba, maja), hades));
        // Ola (12+) has it by a manual grant; groups keep the order category, by hand, no.
        assertEquals("Kuba z kategorii · Ola ręcznie · Maja nie",
                summary(Arrays.asList(ola, kuba, maja), hades));
        // Hidden by hand within the category, and unrated under a category: both "nie".
        assertEquals("Kuba z kategorii · Ola nie", summary(Arrays.asList(ola, kuba),
                knight));
        assertEquals("Ola z kategorii · Kuba nie", summary(Arrays.asList(olaAll, kuba),
                unrated));
        // Nothing loaded yet (no parent PIN): the row stays bare.
        assertNull(summary(null, hades));
        assertNull(summary(Collections.emptyList(), hades));
    }

    private static String summary(List<ChildLibraryFilter.Child> children,
                                  PlayniteLibraryGame game) {
        return ChildLibraryFilter.sharingSummary(RuntimeEnvironment.getApplication(), children,
                game);
    }

    @Test public void theChildSeesNeitherPlayniteHiddenNorLocallyHiddenGames() {
        PlayniteLibraryGame hidden = new PlayniteLibraryGame("steam:2", "Hidden", true, true, 0L,
                "", "", "", "").withAgeRating(rating("E", 6));
        List<PlayniteLibraryGame> listed = ChildLibraryFilter.listed(
                Arrays.asList(hades, hidden, stardew, unrated),
                Collections.singleton("steam:413150"));
        assertEquals(Arrays.asList(hades, unrated), listed);
        // Counts and grid agree: both run over the same listed games.
        ChildGameAccess.Preview preview = ChildGameAccess.preview(ola.ageLimit,
                ola.allowedGameIds, ola.blockedGameIds, listed);
        assertEquals(new ChildLibraryFilter.Selection(ola, ChildLibraryFilter.Group.VISIBLE)
                .filter(listed).size(), preview.visible);
    }

    private List<PlayniteLibraryGame> select(ChildLibraryFilter.Group group) {
        return new ChildLibraryFilter.Selection(ola, group).filter(new ArrayList<>(library));
    }

    private static ChildLibraryFilter.Child child(String id, String ageLimit) {
        return new ChildLibraryFilter.Child(id, id, ageLimit, Collections.emptySet(),
                Collections.emptySet());
    }

    private static GameAgeRating rating(String label, int minAge) {
        try {
            return GameAgeRating.parse(new JSONObject().put("system", "esrb")
                    .put("label", label).put("minAge", minAge));
        } catch (org.json.JSONException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static PlayniteLibraryGame game(String id, GameAgeRating rating) {
        return new PlayniteLibraryGame(id, "Game", true, false, 0L, "", "", "", "")
                .withAgeRating(rating);
    }
}
