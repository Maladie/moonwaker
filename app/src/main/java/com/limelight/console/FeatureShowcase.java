package com.limelight.console;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What "What MoonWaker can do" lists: the static card table plus the two decisions about it,
 * which cards this TV and host support and which of them the user has not seen yet. Pure; the
 * screen ({@link FeatureShowcaseScreen}) draws it and the caller supplies the facts.
 */
final class FeatureShowcase {
    /** What a card needs; the caller reports which of these this TV and host satisfy. */
    enum Need {
        NONE, HOST, HOST_SUSPEND, HOST_GAMES, HOST_CHILDREN, HOST_USB, HOST_DISCORD, PAD,
        PAD_GYRO, GOOGLE_TV, HOST_PLUS
    }

    /**
     * One card. The artwork comes from docs/MoonWaker/art/make_feature_showcase.py: the card
     * header and a scene of three numbered frames, one per step.
     */
    enum Feature {
        START("start", false, false, Need.HOST, 0.56f,
                R.drawable.feature_card_start, R.drawable.feature_scene_start,
                R.string.feature_start_title, R.string.feature_start_summary,
                R.string.feature_start_heading, R.string.feature_start_needs,
                R.string.feature_start_where, R.string.feature_start_step1,
                R.string.feature_start_step2, R.string.feature_start_step3),
        SUSPEND("suspend", true, false, Need.HOST_SUSPEND, 0.62f,
                R.drawable.feature_card_suspend, R.drawable.feature_scene_suspend,
                R.string.feature_suspend_title, R.string.feature_suspend_summary,
                R.string.feature_suspend_heading, R.string.feature_suspend_needs,
                R.string.feature_suspend_where, R.string.feature_suspend_step1,
                R.string.feature_suspend_step2, R.string.feature_suspend_step3),
        GAMES("games", false, false, Need.HOST_GAMES, 0.36f,
                R.drawable.feature_card_games, R.drawable.feature_scene_games,
                R.string.feature_games_title, R.string.feature_games_summary,
                R.string.feature_games_heading, R.string.feature_games_needs,
                R.string.feature_games_where, R.string.feature_games_step1,
                R.string.feature_games_step2, R.string.feature_games_step3),
        CHILDREN("children", false, false, Need.HOST_CHILDREN, 0.08f,
                R.drawable.feature_card_children, R.drawable.feature_scene_children,
                R.string.feature_children_title, R.string.feature_children_summary,
                R.string.feature_children_heading, R.string.feature_children_needs,
                R.string.feature_children_where, R.string.feature_children_step1,
                R.string.feature_children_step2, R.string.feature_children_step3),
        GYRO("gyro", true, true, Need.PAD_GYRO, 0.52f,
                R.drawable.feature_card_gyro, R.drawable.feature_scene_gyro,
                R.string.feature_gyro_title, R.string.feature_gyro_summary,
                R.string.feature_gyro_heading, R.string.feature_gyro_needs,
                R.string.feature_gyro_where, R.string.feature_gyro_step1,
                R.string.feature_gyro_step2, R.string.feature_gyro_step3),
        HAPTICS("haptics", true, true, Need.PAD, 0.92f,
                R.drawable.feature_card_haptics, R.drawable.feature_scene_haptics,
                R.string.feature_haptics_title, R.string.feature_haptics_summary,
                R.string.feature_haptics_heading, R.string.feature_haptics_needs,
                R.string.feature_haptics_where, R.string.feature_haptics_step1,
                R.string.feature_haptics_step2, R.string.feature_haptics_step3),
        USB("usb", true, true, Need.HOST_USB, 0.78f,
                R.drawable.feature_card_usb, R.drawable.feature_scene_usb,
                R.string.feature_usb_title, R.string.feature_usb_summary,
                R.string.feature_usb_heading, R.string.feature_usb_needs,
                R.string.feature_usb_where, R.string.feature_usb_step1,
                R.string.feature_usb_step2, R.string.feature_usb_step3),
        QUALITY("quality", false, false, Need.NONE, 0.45f,
                R.drawable.feature_card_quality, R.drawable.feature_scene_quality,
                R.string.feature_quality_title, R.string.feature_quality_summary,
                R.string.feature_quality_heading, R.string.feature_quality_needs,
                R.string.feature_quality_where, R.string.feature_quality_step1,
                R.string.feature_quality_step2, R.string.feature_quality_step3),
        FREEZE("freeze", true, false, Need.NONE, 0.58f,
                R.drawable.feature_card_suspend, R.drawable.feature_scene_suspend,
                R.string.feature_freeze_title, R.string.feature_freeze_summary,
                R.string.feature_freeze_heading, R.string.feature_freeze_needs,
                R.string.feature_freeze_where, R.string.feature_freeze_step1,
                R.string.feature_freeze_step2, R.string.feature_freeze_step3),
        AUTOPILOT("autopilot", false, false, Need.NONE, 0.28f,
                R.drawable.feature_card_autopilot, R.drawable.feature_scene_autopilot,
                R.string.feature_autopilot_title, R.string.feature_autopilot_summary,
                R.string.feature_autopilot_heading, R.string.feature_autopilot_needs,
                R.string.feature_autopilot_where, R.string.feature_autopilot_step1,
                R.string.feature_autopilot_step2, R.string.feature_autopilot_step3),
        DISCORD("discord", false, false, Need.HOST_DISCORD, 0.69f,
                R.drawable.feature_card_discord, R.drawable.feature_scene_discord,
                R.string.feature_discord_title, R.string.feature_discord_summary,
                R.string.feature_discord_heading, R.string.feature_discord_needs,
                R.string.feature_discord_where, R.string.feature_discord_step1,
                R.string.feature_discord_step2, R.string.feature_discord_step3),
        CONTINUE_TILE("continue", true, false, Need.GOOGLE_TV, 0.15f,
                R.drawable.feature_card_continue, R.drawable.feature_scene_continue,
                R.string.feature_continue_title, R.string.feature_continue_summary,
                R.string.feature_continue_heading, R.string.feature_continue_needs,
                R.string.feature_continue_where, R.string.feature_continue_step1,
                R.string.feature_continue_step2, R.string.feature_continue_step3),
        GAME_MODE("gamemode", true, false, Need.HOST_GAMES, 0.99f,
                R.drawable.feature_card_gamemode, R.drawable.feature_scene_gamemode,
                R.string.feature_gamemode_title, R.string.feature_gamemode_summary,
                R.string.feature_gamemode_heading, R.string.feature_gamemode_needs,
                R.string.feature_gamemode_where, R.string.feature_gamemode_step1,
                R.string.feature_gamemode_step2, R.string.feature_gamemode_step3),
        CAPTURES("captures", true, false, Need.HOST_PLUS, 0.86f,
                R.drawable.feature_card_captures, R.drawable.feature_scene_captures,
                R.string.feature_captures_title, R.string.feature_captures_summary,
                R.string.feature_captures_heading, R.string.feature_captures_needs,
                R.string.feature_captures_where, R.string.feature_captures_step1,
                R.string.feature_captures_step2, R.string.feature_captures_step3);

        final String id;
        /** Introduced since the last big release: shows NEW and counts for "What's new". */
        final boolean isNew;
        final boolean experimental;
        final Need need;
        /** Hue of the artwork, for the focus glow and the screen's blooms. */
        final float hue;
        final int card, scene, title, summary, heading, needs, where;
        final int[] steps;

        Feature(String id, boolean isNew, boolean experimental, Need need, float hue,
                int card, int scene, int title, int summary, int heading, int needs,
                int where, int... steps) {
            this.id = id;
            this.isNew = isNew;
            this.experimental = experimental;
            this.need = need;
            this.hue = hue;
            this.card = card;
            this.scene = scene;
            this.title = title;
            this.summary = summary;
            this.heading = heading;
            this.needs = needs;
            this.where = where;
            this.steps = steps;
        }

        /** Gyro and haptics are the cards with a global default to flip. */
        boolean hasDefaultToggle() {
            return this == GYRO || this == HAPTICS;
        }
    }

    private FeatureShowcase() {
    }

    /** Catalog order, minus the cards whose need is not met. */
    static List<Feature> visible(Set<Need> met) {
        List<Feature> shown = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            if (feature.need == Need.NONE || met.contains(feature.need)) shown.add(feature);
        }
        return shown;
    }

    /**
     * Cards to announce after an update. {@code seen} is the stored set, null when none was ever
     * stored: an upgrade from a build without the screen announces the new cards, a fresh
     * install announces nothing (the first-run wizard shows the screen).
     */
    static List<Feature> unseen(Set<String> seen, List<Feature> visible, boolean upgraded) {
        List<Feature> fresh = new ArrayList<>();
        for (Feature feature : visible) {
            boolean show = seen == null ? upgraded && feature.isNew : !seen.contains(feature.id);
            if (show) fresh.add(feature);
        }
        return fresh;
    }

    /** The set to store once {@code shown} was announced. */
    static Set<String> markSeen(Set<String> seen, Collection<Feature> shown) {
        Set<String> next = seen == null ? new HashSet<>() : new HashSet<>(seen);
        for (Feature feature : shown) next.add(feature.id);
        return next;
    }

    /** Every card, for the first install that announces nothing. */
    static Set<String> allIds() {
        return markSeen(null, EnumSet.allOf(Feature.class));
    }
}
