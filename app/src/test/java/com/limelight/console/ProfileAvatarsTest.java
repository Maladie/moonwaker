package com.limelight.console;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProfileAvatarsTest {
    @Test public void avatarIdsAreOnlyTheGatewayCatalogShape() {
        assertTrue(HostGatewayClient.isAvatarId("builtin:lorelei-luna"));
        assertTrue(HostGatewayClient.isAvatarId("custom:my-dog_2"));
        for (String invalid : new String[]{null, "", "purple", "default", "builtin:",
                "custom:../gateway", "custom:Dog", "other:x", "builtin:-x"}) {
            assertFalse(invalid, HostGatewayClient.isAvatarId(invalid));
        }
    }

    @Test public void catalogKeepsHostOrderAndDropsMalformedEntries() throws Exception {
        JSONArray avatars = new JSONArray()
                .put(avatar("builtin:voxel-kai", "0a1b", "voxel"))
                .put(avatar("custom:../x", "ff", "custom"))
                .put(avatar("custom:dog", "not hex", "custom"))
                .put(avatar("custom:owl", "ff", "Bad Group"))
                .put("text")
                .put(avatar("custom:cat", "beef", "custom"))
                .put(avatar("custom:cat", "dead", "custom"));
        List<HostGatewayClient.Avatar> parsed = HostGatewayClient.parseAvatars(
                new JSONObject().put("avatars", avatars));
        assertEquals(Arrays.asList("builtin:voxel-kai", "custom:cat"),
                ProfileAvatars.avatarIds(parsed));
        assertEquals("beef", parsed.get(1).revision);
        assertEquals("voxel", parsed.get(0).group);
    }

    @Test public void catalogIsBounded() throws Exception {
        JSONArray avatars = new JSONArray();
        for (int index = 0; index < 300; index++) {
            avatars.put(avatar("custom:a" + index, "1", "custom"));
        }
        assertEquals(160, HostGatewayClient.parseAvatars(
                new JSONObject().put("avatars", avatars)).size());
    }

    @Test public void childDraftTakesTheGeneratedArtOrACatalogId() throws Exception {
        assertEquals("", HostGatewayClient.childDraftBody(draft("")).getString("avatar_id"));
        try {
            HostGatewayClient.childDraftBody(draft("green"));
            fail("a colour preset is not an avatar");
        } catch (IllegalArgumentException expected) {
            // The draft is rejected before anything is sent.
        }
    }

    /**
     * The owner's host still had children saved with a colour name ("green"); their draft held it
     * and every save failed on the TV before a request was sent.
     */
    @Test public void childWithAColourNameSavesAsTheGeneratedArt() throws Exception {
        JSONObject child = new JSONObject().put("id", "p-child").put("name", "Ola")
                .put("avatar_id", "green").put("enabled", true)
                .put("parent_profile_id", "p-parent");
        HostGatewayClient.ChildProfile parsed = HostGatewayClient.parseChildProfile(child, "");
        assertEquals("", parsed.avatarId);
        ConsoleChildProfileEditor.Draft draft = new ConsoleChildProfileEditor.Draft(parsed.id,
                parsed.name, parsed.avatarId, parsed.enabled, parsed.weekdays, parsed.ageLimit,
                Collections.emptySet(), Collections.emptySet(), 3, false, parsed.gameMode,
                parsed.gameModeWindows, parsed.gameModeMute);
        assertEquals("", HostGatewayClient.childDraftBody(draft).getString("avatar_id"));
        child.put("avatar_id", "builtin:animal-frog");
        assertEquals("builtin:animal-frog",
                HostGatewayClient.parseChildProfile(child, "").avatarId);
    }

    @Test public void newChildStartsWithTheGeneratedArt() {
        assertEquals("", ConsoleChildProfileEditor.Draft.newChild(0).avatar);
    }

    private static JSONObject avatar(String id, String revision, String group) throws Exception {
        return new JSONObject().put("id", id).put("revision", revision).put("group", group);
    }

    private static ConsoleChildProfileEditor.Draft draft(String avatar) {
        List<ConsoleChildProfileEditor.Day> days = new ArrayList<>();
        for (int index = 0; index < 7; index++) days.add(ConsoleChildProfileEditor.Day.disabled());
        return new ConsoleChildProfileEditor.Draft("", "Kid", avatar, true, days,
                ChildGameAccess.MANUAL, Collections.emptySet(), Collections.emptySet(), 0, false,
                false, true, true);
    }
}
