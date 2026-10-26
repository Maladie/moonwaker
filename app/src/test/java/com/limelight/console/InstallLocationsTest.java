package com.limelight.console;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallLocationsTest {
    private static final String RESPONSE = "{\"ok\":true,\"targets\":{\"steam\":["
            + "{\"path\":\"C:\\\\Steam\",\"free_bytes\":10,\"total_bytes\":20,\"default\":true},"
            + "{\"path\":\"D:\\\\SteamLibrary\",\"free_bytes\":-5,\"total_bytes\":30},"
            + "{\"path\":\"  \"},\"junk\"],\"epic\":[]}}";

    @Test public void parsesOneLauncherAndDropsInvalidRows() throws Exception {
        List<InstallLocations.Target> steam =
                InstallLocations.parse(new JSONObject(RESPONSE), InstallLocations.STEAM);
        assertEquals(2, steam.size());
        assertEquals("C:\\Steam", steam.get(0).path);
        assertTrue(steam.get(0).hostDefault);
        assertEquals(0, steam.get(1).freeBytes);
        assertTrue(InstallLocations.parse(new JSONObject(RESPONSE), InstallLocations.EPIC)
                .isEmpty());
        assertTrue(InstallLocations.parse(null, InstallLocations.STEAM).isEmpty());
    }

    @Test public void preselectsDefaultThenLastThenHostDefault() throws Exception {
        List<InstallLocations.Target> steam =
                InstallLocations.parse(new JSONObject(RESPONSE), InstallLocations.STEAM);
        assertEquals(2, InstallLocations.preselected(steam, "d:\\steamlibrary", "C:\\Steam"));
        assertEquals(1, InstallLocations.preselected(steam, "E:\\Gone", "C:\\Steam"));
        assertEquals(0, InstallLocations.preselected(steam, "", "E:\\Gone"));
        assertEquals(0, InstallLocations.preselected(Collections.emptyList(), "C:\\Steam", ""));
    }

    @Test public void onlySteamAndEpicGamesChooseALocation() {
        assertEquals("steam", InstallLocations.launcher(game("Steam")));
        assertEquals("epic", InstallLocations.launcher(game("Epic")));
        assertEquals(null, InstallLocations.launcher(game("GOG")));
        assertEquals(null, InstallLocations.launcher(null));
    }

    private static PlayniteLibraryGame game(String source) {
        return new PlayniteLibraryGame("00000001-0000-0000-0000-000000000000", "Game",
                false, false, false, 0L, "", "", "", "", 0, source, "", false, "",
                "", "", "", -1, false);
    }
}
