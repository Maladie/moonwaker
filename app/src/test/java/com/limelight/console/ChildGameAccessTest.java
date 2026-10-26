package com.limelight.console;

import com.limelight.R;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.limelight.console.ChildGameAccess.State.ABOVE_LIMIT;
import static com.limelight.console.ChildGameAccess.State.AUTO;
import static com.limelight.console.ChildGameAccess.State.BLOCKED;
import static com.limelight.console.ChildGameAccess.State.GRANTED;
import static com.limelight.console.ChildGameAccess.State.MANUAL_ONLY;
import static com.limelight.console.ChildGameAccess.State.UNRATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** AGE_RATINGS_DESIGN_PL.md 3.3/3.4a on the TV: the preview rule, state lines and DTOs. */
public class ChildGameAccessTest {
    @Test public void manualGrantWinsThenHideThenCategory() {
        assertEquals(GRANTED, ChildGameAccess.state("12", true, true, 18));
        assertEquals(BLOCKED, ChildGameAccess.state("12", false, true, 7));
        assertEquals(AUTO, ChildGameAccess.state("12", false, false, 12));
        assertEquals(AUTO, ChildGameAccess.state("12", false, false, 10));
        assertEquals(ABOVE_LIMIT, ChildGameAccess.state("12", false, false, 13));
        // Unrated games only by hand, except with "No limit".
        assertEquals(UNRATED, ChildGameAccess.state("16", false, false, null));
        assertEquals(AUTO, ChildGameAccess.state("all", false, false, null));
        assertEquals(AUTO, ChildGameAccess.state("all", false, false, 18));
        assertEquals(MANUAL_ONLY, ChildGameAccess.state("manual", false, false, 3));
        // Only the six categories exist; the parsers map a missing one to "manual".
        for (String unknown : new String[] {null, "5", "12 ", "ALL"}) {
            try {
                ChildGameAccess.state(unknown, false, false, 3);
                fail("Unknown category " + unknown);
            } catch (IllegalArgumentException expected) {
                // a broken caller
            }
        }
        assertTrue(AUTO.access());
        assertTrue(GRANTED.access());
        assertFalse(BLOCKED.access());
    }

    @Test public void togglingRecomputesTheShownState() {
        // Untouched: the host's own state stays, even a grant inside the category.
        assertEquals(GRANTED, ChildGameAccess.toggled(GRANTED, true, "16", 12));
        // On -> off of a category game hides it by hand.
        assertEquals(BLOCKED, ChildGameAccess.toggled(AUTO, false, "16", 13));
        // Off -> on of a hidden game only lifts the hide.
        assertEquals(AUTO, ChildGameAccess.toggled(BLOCKED, true, "16", 13));
        // Off -> on above the category is a manual grant, and warns.
        assertEquals(GRANTED, ChildGameAccess.toggled(ABOVE_LIMIT, true, "12", 13));
        assertTrue(ChildGameAccess.warns(GRANTED, "12", 13));
        assertFalse(ChildGameAccess.warns(GRANTED, "manual", 13));
        assertFalse(ChildGameAccess.warns(GRANTED, "12", null));
        // On -> off of a manual grant falls back to what the category says.
        assertEquals(ABOVE_LIMIT, ChildGameAccess.toggled(GRANTED, false, "12", 13));
        assertEquals(UNRATED, ChildGameAccess.toggled(GRANTED, false, "12", null));
        assertEquals(MANUAL_ONLY, ChildGameAccess.toggled(GRANTED, false, "manual", 3));
        assertEquals(GRANTED, ChildGameAccess.toggled(MANUAL_ONLY, true, "manual", 3));
        // A saved switch-off of a grant inside the category is a hide; on again only unhides.
        ChildGameAccess.State saved = ChildGameAccess.toggled(GRANTED, false, "16", 12);
        assertEquals(BLOCKED, saved);
        assertEquals(AUTO, ChildGameAccess.toggled(saved, true, "16", 12));
    }

    @Test public void stateLinesFollowMockups23eAnd23f() {
        assertEquals(R.string.child_game_state_auto, ChildGameAccess.stateText(AUTO, "16", 13));
        assertEquals(R.string.child_game_state_auto_all,
                ChildGameAccess.stateText(AUTO, "all", null));
        assertEquals(R.string.child_game_state_granted_above,
                ChildGameAccess.stateText(GRANTED, "12", 13));
        assertEquals(R.string.child_game_state_granted_unrated,
                ChildGameAccess.stateText(GRANTED, "12", null));
        assertEquals(R.string.child_game_state_granted,
                ChildGameAccess.stateText(GRANTED, "manual", 13));
        assertEquals(R.string.child_game_state_blocked_within,
                ChildGameAccess.stateText(BLOCKED, "16", 13));
        assertEquals(R.string.child_game_state_blocked,
                ChildGameAccess.stateText(BLOCKED, "all", 13));
        assertEquals(R.string.child_game_state_above,
                ChildGameAccess.stateText(ABOVE_LIMIT, "12", 13));
        assertEquals(R.string.child_game_state_unrated,
                ChildGameAccess.stateText(UNRATED, "12", null));
        assertEquals(R.string.child_game_state_manual_only,
                ChildGameAccess.stateText(MANUAL_ONLY, "manual", 3));
    }

    @Test public void previewCountsTheParentLibrary() {
        PlayniteLibraryGame hades = game("steam:1145360", rating("esrb", "T", 13));
        PlayniteLibraryGame knight = game("steam:367520", rating("esrb", "E10+", 10));
        PlayniteLibraryGame stardew = game("steam:413150", rating("pegi", "3", 3));
        PlayniteLibraryGame unrated = game("epic:Fortnite", null);
        PlayniteLibraryGame guid = game("11223344-5566-7788-99AA-BBCCDDEEFF00", null);
        Set<String> allowed = new HashSet<>(Arrays.asList("steam:1145360",
                "playnite:11223344-5566-7788-99aa-bbccddeeff00"));
        Set<String> blocked = Collections.singleton("steam:367520");

        ChildGameAccess.Preview twelve = ChildGameAccess.preview("12", allowed, blocked,
                Arrays.asList(hades, knight, stardew, unrated, guid));
        assertEquals(3, twelve.visible);
        assertEquals(2, twelve.granted);
        assertEquals(1, twelve.blocked);
        assertEquals(1, twelve.unrated);

        ChildGameAccess.Preview all = ChildGameAccess.preview("all", allowed, blocked,
                Arrays.asList(hades, knight, stardew, unrated, guid));
        assertEquals(4, all.visible);
        assertEquals(0, all.unrated);
    }

    @Test public void ratingParsesOnlyAKnownSystemWithAMinimumAge() throws Exception {
        GameAgeRating teen = GameAgeRating.parse(new JSONObject("{\"system\":\"ESRB\","
                + "\"label\":\"T\",\"minAge\":13,\"descriptors\":[\"Blood\",\" \",\"Violence\"],"
                + "\"source\":\"steam\"}"));
        assertEquals("esrb", teen.system);
        assertEquals(13, teen.minAge);
        assertEquals(Arrays.asList("Blood", "Violence"), teen.descriptors);
        assertEquals("ESRB Teen", teen.systemText());
        assertEquals(teen, GameAgeRating.parse(teen.toJson()));
        assertNull(GameAgeRating.parse(null));
        assertNull(GameAgeRating.parse(new JSONObject("{\"system\":\"rp\",\"label\":\"RP\","
                + "\"minAge\":0}")));
        assertNull(GameAgeRating.parse(new JSONObject("{\"system\":\"pegi\",\"label\":\"16\"}")));
        assertEquals("PEGI 16", rating("pegi", "16", 16).systemText());
        assertEquals("ESRB Everyone 10+", rating("esrb", "E10+", 10).systemText());
        assertEquals("ESRB Early Childhood", rating("esrb", "EC", 3).systemText());
        assertEquals("ESRB Adults Only", rating("esrb", "AO", 18).systemText());
    }

    @Test public void badgeColourFollowsTheMinimumAge() {
        assertEquals(GameAgeRating.GREEN, GameAgeRating.color(rating("pegi", "7", 7)));
        assertEquals(GameAgeRating.YELLOW, GameAgeRating.color(rating("esrb", "E10+", 10)));
        assertEquals(GameAgeRating.YELLOW, GameAgeRating.color(rating("pegi", "12", 12)));
        assertEquals(GameAgeRating.ORANGE, GameAgeRating.color(rating("esrb", "T", 13)));
        assertEquals(GameAgeRating.ORANGE, GameAgeRating.color(rating("usk", "16", 16)));
        assertEquals(GameAgeRating.RED, GameAgeRating.color(rating("esrb", "M", 17)));
        assertEquals(GameAgeRating.GREY, GameAgeRating.color(null));
    }

    @Test public void parentLibraryRecordCarriesItsRating() throws Exception {
        HostGatewayClient.PlayniteLibrary library = HostGatewayClient.parsePlayniteLibrary(
                new JSONObject("{\"games\":[{\"id\":\"steam:1245620\",\"name\":\"Cyberpunk\","
                        + "\"ageRating\":{\"system\":\"pegi\",\"label\":\"16\",\"minAge\":16}},"
                        + "{\"id\":\"steam:1\",\"name\":\"Unrated\"}]}"));
        assertEquals(16, library.games.get(0).ageRating.minAge);
        assertNull(library.games.get(1).ageRating);
    }

    @Test public void childProfileCarriesCategoryAndCanonicalLists() throws Exception {
        HostGatewayClient.ChildProfile child = HostGatewayClient.parseChildProfile(
                new JSONObject("{\"id\":\"c1\",\"parent_profile_id\":\"p\",\"age_limit\":\"12\","
                        + "\"allowed_game_ids\":[\"Steam:1\",\"11223344-5566-7788-99aa-bbccddeeff00\"],"
                        + "\"blocked_game_ids\":[\"epic:Fortnite\"]}"), "");
        assertEquals("12", child.ageLimit);
        assertEquals(new HashSet<>(Arrays.asList("steam:1",
                "playnite:11223344-5566-7788-99aa-bbccddeeff00")), child.allowedGameIds);
        assertEquals(Collections.singleton("epic:Fortnite"), child.blockedGameIds);
        assertEquals("manual", HostGatewayClient.parseChildProfile(
                new JSONObject("{\"id\":\"c2\"}"), "p").ageLimit);
        for (String limit : new String[] {"\"5\"", "\"\"", "null", "12", "\"All\""}) {
            try {
                HostGatewayClient.parseChildProfile(new JSONObject(
                        "{\"id\":\"c3\",\"age_limit\":" + limit + "}"), "p");
                fail("An unknown age_limit must be rejected: " + limit);
            } catch (IOException expected) {
                // invalid_response
            }
        }
    }

    @Test public void sharingReadsStatePerChildAndRejectsAnUnknownOne() throws Exception {
        HostGatewayClient.ChildSharing sharing = HostGatewayClient.parseChildSharing(
                new JSONObject("{\"parent_profile_id\":\"p\",\"revision\":4,\"min_age\":13,"
                        + "\"ageRating\":{\"system\":\"esrb\",\"label\":\"T\",\"minAge\":13},"
                        + "\"children\":[{\"id\":\"a\",\"name\":\"Ola\",\"age_limit\":\"12\","
                        + "\"granted\":false,\"state\":\"above_limit\"},"
                        + "{\"id\":\"b\",\"name\":\"Kuba\",\"age_limit\":\"16\","
                        + "\"granted\":true,\"state\":\"auto\"},"
                        + "{\"id\":\"c\",\"name\":\"Zosia\",\"state\":\"manual_only\"}]}"));
        assertEquals(13, sharing.ageRating.minAge);
        assertEquals(Integer.valueOf(13), sharing.minAge);
        assertEquals(ABOVE_LIMIT, sharing.children.get(0).state);
        assertEquals("12", sharing.children.get(0).ageLimit);
        assertEquals(AUTO, sharing.children.get(1).state);
        assertEquals("manual", sharing.children.get(2).ageLimit);
        HostGatewayClient.ChildSharing unrated = HostGatewayClient.parseChildSharing(
                new JSONObject("{\"ageRating\":null,\"min_age\":null,\"children\":[]}"));
        assertNull(unrated.ageRating);
        assertNull(unrated.minAge);
        for (String invalid : new String[] {
                "{\"min_age\":null,\"children\":[{\"id\":\"a\",\"granted\":true}]}",
                "{\"children\":[]}",
                "{\"min_age\":\"13\",\"children\":[]}",
                "{\"min_age\":13.5,\"children\":[]}",
                "{\"min_age\":13,\"children\":[{\"id\":\"a\",\"age_limit\":\"18\","
                        + "\"state\":\"auto\"}]}"}) {
            try {
                HostGatewayClient.parseChildSharing(new JSONObject(invalid));
                fail("Invalid sharing/get must be rejected: " + invalid);
            } catch (IOException expected) {
                // invalid_response
            }
        }
    }

    private static GameAgeRating rating(String system, String label, int minAge) {
        try {
            return GameAgeRating.parse(new JSONObject().put("system", system)
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
