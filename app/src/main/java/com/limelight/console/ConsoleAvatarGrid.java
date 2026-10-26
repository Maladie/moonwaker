package com.limelight.console;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The avatar picker (mockup 21-avatar-picker-v4): the profile's generated art first, then the
 * host's images under a header per group, five to a row. The focused avatar grows inside a
 * white ring, the chosen one carries the cyan check of mockup 20c. Choosing hands its id to
 * the caller ("" for the generated art), which owns saving.
 */
final class ConsoleAvatarGrid {
    static final int COLUMNS = 5;
    static final int CELL_DP = 62;
    static final int AVATAR_DP = 48;
    static final int CHECK_DP = 18;
    static final float FOCUS_SCALE = 1.12f;
    static final int CYAN = 0xFF73D7FF;
    static final int PLACEHOLDER = 0xFF273642;

    private ConsoleAvatarGrid() { }

    /** {@code seedId} and {@code name} draw the generated art; {@code avatars} the host's. */
    static GridLayout create(Context context, String seedId, String name,
                             List<HostGatewayClient.Avatar> avatars, String current,
                             Consumer<String> onPick) {
        float density = context.getResources().getDisplayMetrics().density;
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        int inset = Math.round(ConsolePanel.ROW_INSET_DP * density);
        grid.setPadding(inset, Math.round(4 * density), inset, Math.round(8 * density));
        boolean offered = current.isEmpty();
        for (HostGatewayClient.Avatar avatar : avatars) offered |= avatar.id.equals(current);
        String chosen = offered ? current : "";
        List<View> checks = new ArrayList<>();
        Map<String, Integer> groupSizes = new HashMap<>();
        for (HostGatewayClient.Avatar avatar : avatars) groupSizes.merge(avatar.group, 1, Integer::sum);
        int row = 0;
        int column = 0;
        int inGroup = 0;
        String group = null;
        List<String> ids = new ArrayList<>();
        ids.add("");
        for (HostGatewayClient.Avatar avatar : avatars) ids.add(avatar.id);
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            if (index > 0 && !avatars.get(index - 1).group.equals(group)) {
                group = avatars.get(index - 1).group;
                if (column > 0) row++;
                GridLayout.LayoutParams headerParams = new GridLayout.LayoutParams(
                        GridLayout.spec(row++), GridLayout.spec(0, COLUMNS));
                headerParams.topMargin = Math.round(10 * density);
                grid.addView(header(context, groupLabel(context, group)), headerParams);
                column = 0;
                inGroup = 0;
            }
            inGroup++;
            FrameLayout cell = cell(context, density);
            cell.setTag("avatar.choice:" + id);
            cell.addView(avatar(context, density, seedId, name, id), centred(AVATAR_DP, density));
            View check = check(context, density);
            check.setVisibility(id.equals(chosen) ? View.VISIBLE : View.INVISIBLE);
            checks.add(check);
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    Math.round(CHECK_DP * density), Math.round(CHECK_DP * density),
                    Gravity.CENTER);
            checkParams.leftMargin = Math.round(AVATAR_DP / 2f * .7f * density);
            checkParams.topMargin = checkParams.leftMargin;
            cell.addView(check, checkParams);
            cell.setContentDescription(id.isEmpty()
                    ? context.getString(R.string.console_avatar_generated)
                    : context.getString(R.string.console_avatar_item, inGroup,
                    groupSizes.get(group)) + ", " + groupLabel(context, group));
            cell.setOnClickListener(view -> {
                for (View other : checks) other.setVisibility(View.INVISIBLE);
                check.setVisibility(View.VISIBLE);
                onPick.accept(id);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(row), GridLayout.spec(column));
            params.width = Math.round(CELL_DP * density);
            params.height = params.width;
            grid.addView(cell, params);
            if (++column == COLUMNS) {
                column = 0;
                row++;
            }
        }
        return grid;
    }

    /** The picker's name of a host group; one this app does not know shows as it is. */
    static String groupLabel(Context context, String group) {
        switch (group) {
            case "cyber": return context.getString(R.string.console_avatar_group_cyber);
            case "fantasy": return context.getString(R.string.console_avatar_group_fantasy);
            case "fun": return context.getString(R.string.console_avatar_group_fun);
            case "voxel": return context.getString(R.string.console_avatar_group_voxel);
            case "custom": return context.getString(R.string.console_avatar_group_custom);
            default: return group;
        }
    }

    private static TextView header(Context context, String label) {
        TextView header = new TextView(context);
        header.setText(label.toUpperCase(Locale.getDefault()));
        header.setTextSize(ConsolePanel.CAPTION_SP);
        header.setLetterSpacing(.12f);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setTextColor(ConsolePanel.HEADER);
        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return header;
    }

    private static FrameLayout cell(Context context, float density) {
        FrameLayout cell = new FrameLayout(context);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Math.round(2 * density), ConsolePanel.FOCUS_FILL);
        StateListDrawable focus = new StateListDrawable();
        focus.addState(new int[]{android.R.attr.state_focused}, ring);
        cell.setForeground(focus);
        cell.setOnFocusChangeListener((view, focused) -> {
            float scale = focused ? FOCUS_SCALE : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
        return cell;
    }

    private static View avatar(Context context, float density, String seedId, String name,
                               String id) {
        FrameLayout frame = new FrameLayout(context);
        ImageView image = new ImageView(context);
        frame.addView(image, centred(AVATAR_DP, density));
        if (id.isEmpty()) {
            image.setImageDrawable(new ConsoleProfileSelectView.RoundArt(HostTileArt.avatar(seedId)));
            TextView initials = new TextView(context);
            initials.setText(ConsoleProfileSelectView.initials(name == null ? "" : name.trim()));
            initials.setTextSize(16f);
            initials.setTextColor(Color.WHITE);
            initials.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            initials.setGravity(Gravity.CENTER);
            initials.setIncludeFontPadding(false);
            frame.addView(initials, centred(AVATAR_DP, density));
            return frame;
        }
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setShape(GradientDrawable.OVAL);
        placeholder.setColor(PLACEHOLDER);
        image.setImageDrawable(placeholder);
        ProfileAvatars.bind(image, id, bitmap ->
                image.setImageDrawable(new ConsoleProfileSelectView.RoundArt(bitmap)));
        return frame;
    }

    private static View check(Context context, float density) {
        TextView check = new TextView(context);
        check.setText("✓");
        check.setTextSize(11f);
        check.setTypeface(Typeface.DEFAULT_BOLD);
        check.setTextColor(ConsolePanel.INK);
        check.setGravity(Gravity.CENTER);
        check.setIncludeFontPadding(false);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(CYAN);
        dot.setStroke(Math.round(2 * density), 0xFF11131A);
        check.setBackground(dot);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return check;
    }

    private static FrameLayout.LayoutParams centred(int sizeDp, float density) {
        int size = Math.round(sizeDp * density);
        return new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
    }
}
