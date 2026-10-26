package com.limelight.console;

import android.content.Context;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parent library's "For child" filter (AGE_RATINGS_DESIGN_PL.md 3.5, mockup 23g): the grid
 * shows what one child would see, by {@link ChildGameAccess}'s preview rule. The children are the
 * list child management last loaded for a host and parent profile, kept in memory only (the
 * Gateway owns them, and every sharing save reloads them); a choice holds its own copy of its
 * child, so it works without a reload.
 */
final class ChildLibraryFilter {
    /** What the grid narrows to: the child's library, or one of the editor's counts (23a). */
    enum Group { VISIBLE, GRANTED, BLOCKED, UNRATED }

    /** One child's age category and manual lists, as the host last reported them. */
    static final class Child {
        final String id;
        final String name;
        final String ageLimit;
        final Set<String> allowedGameIds;
        final Set<String> blockedGameIds;

        Child(String id, String name, String ageLimit, Collection<String> allowedGameIds,
              Collection<String> blockedGameIds) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.ageLimit = ChildGameAccess.ageLimit(ageLimit);
            this.allowedGameIds = canonical(allowedGameIds);
            this.blockedGameIds = canonical(blockedGameIds);
        }

        ChildGameAccess.State state(PlayniteLibraryGame game) {
            String id = ChildGameAccess.canonicalGameId(game.playniteGameId);
            return ChildGameAccess.state(ageLimit, allowedGameIds.contains(id),
                    blockedGameIds.contains(id), minAge(game));
        }

        private static Set<String> canonical(Collection<String> ids) {
            Set<String> result = new LinkedHashSet<>();
            if (ids != null) for (String id : ids) result.add(ChildGameAccess.canonicalGameId(id));
            return Collections.unmodifiableSet(result);
        }
    }

    static final class Selection {
        final Child child;
        final Group group;

        Selection(Child child, Group group) {
            this.child = child;
            this.group = group == null ? Group.VISIBLE : group;
        }

        boolean shows(PlayniteLibraryGame game) {
            ChildGameAccess.State state = child.state(game);
            switch (group) {
                case GRANTED: return state == ChildGameAccess.State.GRANTED;
                case BLOCKED: return state == ChildGameAccess.State.BLOCKED;
                case UNRATED: return state == ChildGameAccess.State.UNRATED;
                default: return state.access();
            }
        }

        List<PlayniteLibraryGame> filter(List<PlayniteLibraryGame> games) {
            List<PlayniteLibraryGame> shown = new ArrayList<>();
            for (PlayniteLibraryGame game : games) if (shows(game)) shown.add(game);
            return shown;
        }
    }

    private final Map<String, List<Child>> children = new HashMap<>();
    private final Map<String, Selection> selections = new HashMap<>();
    /** The latest load started per key: an older one landing later changes nothing. */
    private final Map<String, Integer> loads = new HashMap<>();

    static String key(String hostId, String parentProfileId) {
        return hostId + '|' + parentProfileId;
    }

    static Integer minAge(PlayniteLibraryGame game) {
        return game.ageRating == null ? null : game.ageRating.minAge;
    }

    /**
     * The parent's games the child's own TV lists at all (3.5: the grid and the editor's counts
     * show exactly that): neither hidden in Playnite nor hidden on this TV.
     */
    static List<PlayniteLibraryGame> listed(List<PlayniteLibraryGame> games,
                                            Set<String> locallyHidden) {
        List<PlayniteLibraryGame> listed = new ArrayList<>();
        for (PlayniteLibraryGame game : games) {
            if (!game.hidden && !locallyHidden.contains(game.playniteGameId)) listed.add(game);
        }
        return listed;
    }

    /** The children of {@code profiles} that belong to {@code parentProfileId}. */
    static List<Child> of(HostGatewayClient.ChildProfiles profiles, String parentProfileId) {
        List<Child> result = new ArrayList<>();
        for (HostGatewayClient.ChildProfile child : profiles.children) {
            if (child == null || child.id.isEmpty()
                    || !child.parentProfileId.equals(parentProfileId)) continue;
            result.add(new Child(child.id, child.name, child.ageLimit, child.allowedGameIds,
                    child.blockedGameIds));
        }
        return result;
    }

    /**
     * Mockup 23d: the second line of "Share game with child" in Game options, what each child
     * has of {@code game} by {@link Child#state}, e.g. "Ola, Kuba by category &middot; Maja not
     * shared"; null without loaded children (the row stays bare).
     */
    static String sharingSummary(Context context, List<Child> children,
                                 PlayniteLibraryGame game) {
        if (children == null || children.isEmpty()) return null;
        List<String> auto = new ArrayList<>();
        List<String> granted = new ArrayList<>();
        List<String> none = new ArrayList<>();
        for (Child child : children) {
            ChildGameAccess.State state = child.state(game);
            (state == ChildGameAccess.State.AUTO ? auto
                    : state == ChildGameAccess.State.GRANTED ? granted : none).add(child.name);
        }
        List<String> parts = new ArrayList<>();
        if (!auto.isEmpty()) {
            parts.add(context.getString(R.string.child_sharing_summary_auto,
                    String.join(", ", auto)));
        }
        if (!granted.isEmpty()) {
            parts.add(context.getString(R.string.child_sharing_summary_granted,
                    String.join(", ", granted)));
        }
        if (!none.isEmpty()) {
            parts.add(context.getString(R.string.child_sharing_summary_none,
                    String.join(", ", none)));
        }
        return String.join(" \u00B7 ", parts);
    }

    /** Null until child management has loaded the children for {@code key}. */
    List<Child> children(String key) {
        return children.get(key);
    }

    Selection selection(String key) {
        return selections.get(key);
    }

    /** Starts a load of {@code key}'s children; its result goes to remember or forget. */
    int begin(String key) {
        int generation = loads.getOrDefault(key, 0) + 1;
        loads.put(key, generation);
        return generation;
    }

    /**
     * Keeps a freshly loaded list; the choice follows its child's new values, or ends when the
     * child is gone. False, changing nothing, when a later load of {@code key} has begun.
     */
    boolean remember(String key, int generation, List<Child> loaded) {
        if (generation != loads.getOrDefault(key, 0)) return false;
        children.put(key, Collections.unmodifiableList(new ArrayList<>(loaded)));
        Selection selection = selections.get(key);
        if (selection == null) return true;
        Child fresh = find(loaded, selection.child.id);
        if (fresh == null) selections.remove(key);
        else selections.put(key, new Selection(fresh, selection.group));
        return true;
    }

    /** Shows {@code child}'s games, or the whole library for null; the child joins the list. */
    void select(String key, Child child, Group group) {
        if (child == null) {
            selections.remove(key);
            return;
        }
        selections.put(key, new Selection(child, group));
        List<Child> known = children.get(key);
        if (known == null) return;
        List<Child> next = new ArrayList<>();
        boolean replaced = false;
        for (Child current : known) {
            boolean same = current.id.equals(child.id);
            next.add(same ? child : current);
            replaced |= same;
        }
        if (!replaced) next.add(child);
        children.put(key, Collections.unmodifiableList(next));
    }

    /**
     * The host's lists for {@code key} could not be reloaded: they and the choice are dropped.
     * False, changing nothing, when a later load of {@code key} has begun.
     */
    boolean forget(String key, int generation) {
        if (generation != loads.getOrDefault(key, 0)) return false;
        children.remove(key);
        selections.remove(key);
        return true;
    }

    private static Child find(List<Child> list, String id) {
        for (Child child : list) if (child.id.equals(id)) return child;
        return null;
    }
}
