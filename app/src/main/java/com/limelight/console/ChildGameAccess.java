package com.limelight.console;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.limelight.R;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which of the parent's games a child sees (AGE_RATINGS_DESIGN_PL.md 3.3): a manual grant wins,
 * then a manual hide, then the child's age category. The Gateway decides; this is the TV's
 * preview of the same rule for the editor's counts and the sharing panel's state lines.
 */
final class ChildGameAccess {
    static final String MANUAL = "manual";
    static final String ALL = "all";
    /** The categories in stepper order (3.4a: strings on the wire). */
    static final List<String> AGE_LIMITS = Arrays.asList(MANUAL, "3", "7", "12", "16", ALL);

    /** sharing/get's per-child {@code state}. */
    enum State {
        /** Within the age category. */
        AUTO("auto"),
        /** Shared by hand. */
        GRANTED("granted"),
        /** Hidden by hand although the category lets it through. */
        BLOCKED("blocked"),
        ABOVE_LIMIT("above_limit"),
        /** No rating and the category is not "all": only a manual grant shares it. */
        UNRATED("unrated"),
        /** The child gets hand-picked games only and this one is not among them. */
        MANUAL_ONLY("manual_only");

        final String wire;

        State(String wire) {
            this.wire = wire;
        }

        boolean access() {
            return this == AUTO || this == GRANTED;
        }

        static State fromWire(String value) {
            for (State state : values()) if (state.wire.equals(value)) return state;
            return null;
        }
    }

    /** Library counts for the editor's "Games" page (mockup 23a). */
    static final class Preview {
        int visible;
        int granted;
        int blocked;
        int unrated;
    }

    private ChildGameAccess() { }

    /**
     * {@code value} when it is one of {@link #AGE_LIMITS}; anything else is a broken caller (the
     * host parsers turn a missing {@code age_limit} into "manual" and reject any other value).
     */
    static String ageLimit(String value) {
        if (!AGE_LIMITS.contains(value)) {
            throw new IllegalArgumentException("Unknown age category: " + value);
        }
        return value;
    }

    /** The 3.3 rule for one game; {@code minAge} null: unrated. */
    static State state(String ageLimit, boolean allowed, boolean blocked, Integer minAge) {
        if (allowed) return State.GRANTED;
        if (blocked) return State.BLOCKED;
        return natural(ageLimit, minAge);
    }

    /** What the category alone does with the game, without manual entries. */
    static State natural(String ageLimit, Integer minAge) {
        String limit = ageLimit(ageLimit);
        if (ALL.equals(limit)) return State.AUTO;
        if (MANUAL.equals(limit)) return State.MANUAL_ONLY;
        if (minAge == null) return State.UNRATED;
        return minAge <= Integer.parseInt(limit) ? State.AUTO : State.ABOVE_LIMIT;
    }

    /**
     * The state a sharing switch now shows: the host's {@code original} while it is untouched,
     * otherwise what the Gateway makes of the new access (3.4: switching on a game the category
     * lets through only lifts its hide; switching off such a game hides it by hand).
     */
    static State toggled(State original, boolean access, String ageLimit, Integer minAge) {
        if (original != null && original.access() == access) return original;
        State natural = natural(ageLimit, minAge);
        if (access) return natural == State.AUTO ? State.AUTO : State.GRANTED;
        return natural == State.AUTO ? State.BLOCKED : natural;
    }

    /** A manual grant above the child's category: the switch and its line take the warning colour. */
    static boolean warns(State state, String ageLimit, Integer minAge) {
        return state == State.GRANTED && natural(ageLimit, minAge) == State.ABOVE_LIMIT;
    }

    /**
     * The line under a child's switch (mockups 23e/23f) as a string resource; formats with
     * {@code %1$s} take {@link #limitText}'s category.
     */
    static int stateText(State state, String ageLimit, Integer minAge) {
        State natural = natural(ageLimit, minAge);
        switch (state) {
            case AUTO:
                return ALL.equals(ageLimit(ageLimit)) ? R.string.child_game_state_auto_all
                        : R.string.child_game_state_auto;
            case GRANTED:
                if (natural == State.ABOVE_LIMIT) return R.string.child_game_state_granted_above;
                if (natural == State.UNRATED) return R.string.child_game_state_granted_unrated;
                return R.string.child_game_state_granted;
            case BLOCKED:
                return natural == State.AUTO && !ALL.equals(ageLimit(ageLimit))
                        ? R.string.child_game_state_blocked_within
                        : R.string.child_game_state_blocked;
            case ABOVE_LIMIT: return R.string.child_game_state_above;
            case UNRATED: return R.string.child_game_state_unrated;
            default: return R.string.child_game_state_manual_only;
        }
    }

    /** {@link #stateText} filled in: the sharing switch's line and the child filter's info. */
    static String stateLine(Context context, State state, String ageLimit, Integer minAge) {
        return context.getString(stateText(state, ageLimit, minAge),
                limitText(context, ageLimit));
    }

    /**
     * Mockup 23g: under the focused game while the "For child" filter is on, its rating, what it
     * is for that child in the sharing panel's words (amber for a grant above the category), and
     * where to change it.
     */
    static CharSequence filterInfo(Context context, GameAgeRating rating, State state,
                                   String ageLimit) {
        Integer minAge = rating == null ? null : rating.minAge;
        SpannableStringBuilder text = new SpannableStringBuilder(
                GameAgeRating.heading(context, rating)).append('\n');
        int start = text.length();
        text.append(stateLine(context, state, ageLimit, minAge));
        if (warns(state, ageLimit, minAge)) {
            text.setSpan(new ForegroundColorSpan(ConsoleTileBadge.AMBER), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text.append('\n').append(context.getString(R.string.child_library_filter_hint,
                context.getString(R.string.playnite_game_options),
                context.getString(R.string.console_child_sharing_action)));
    }

    /** The category as the slider and the state lines name it: "Manual", "12+", "No limit". */
    static String limitText(Context context, String ageLimit) {
        String limit = ageLimit(ageLimit);
        if (MANUAL.equals(limit)) return context.getString(R.string.child_age_limit_manual);
        if (ALL.equals(limit)) return context.getString(R.string.child_age_limit_all);
        return limit + "+";
    }

    /** Mockup 23a's short name under the slider and in the category column ("Wszystkie"). */
    static String stopText(Context context, String ageLimit) {
        return ALL.equals(ageLimit) ? context.getString(R.string.child_age_limit_all_short)
                : limitText(context, ageLimit);
    }

    /** The editor's counts over the parent's library (3.3 per game). */
    static Preview preview(String ageLimit, Set<String> allowed, Set<String> blocked,
                           Collection<PlayniteLibraryGame> library) {
        Preview preview = new Preview();
        for (PlayniteLibraryGame game : library) {
            String id = canonicalGameId(game.playniteGameId);
            Integer minAge = game.ageRating == null ? null : game.ageRating.minAge;
            State state = state(ageLimit, allowed.contains(id), blocked.contains(id), minAge);
            if (state.access()) preview.visible++;
            if (state == State.GRANTED) preview.granted++;
            else if (state == State.BLOCKED) preview.blocked++;
            else if (state == State.UNRATED) preview.unrated++;
        }
        return preview;
    }

    /**
     * The catalog id the host keeps in a child's lists: {@code provider:id} with a lower-case
     * provider, a bare Playnite GUID as {@code playnite:<guid>}.
     */
    static String canonicalGameId(String gameId) {
        String value = gameId == null ? "" : gameId.trim();
        if (value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                + "[0-9a-f]{4}-[0-9a-f]{12}")) {
            return "playnite:" + value.toLowerCase(Locale.ROOT);
        }
        int separator = value.indexOf(':');
        return separator > 0
                ? value.substring(0, separator).toLowerCase(Locale.ROOT)
                + value.substring(separator) : value;
    }
}
