package com.limelight.console;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Community screen's detail: the standard glass side panel (x 1196-1896, y 24-1056 px) with
 * the content of each {@link DiscordCommunityState.Detail} state in the v4 panel rows of
 * {@link ConsolePanel} (mockups 19-discord-together/voice/disconnected-v4). It owns
 * the panel's focus targets; {@link DiscordCommunityView} owns navigation between them.
 */
final class DiscordCommunityDetailPanel {
    private final Context context;
    private final DiscordCommunityView.Callback callback;
    private final ConsolePanel panel;
    final LockableScrollView scroll;
    private final LinearLayout detail;
    final List<View> focusables = new ArrayList<>();
    final Map<String, View> byTag = new HashMap<>();
    final Map<String, View> destinations = new HashMap<>();
    /** The first action of the detail, focused after opening it. */
    View firstAction;
    private DiscordCommunityView.Model model;
    /** Detail state and selection of the rendered content; a rebuild of the same keeps its scroll. */
    private String renderedScreen = "";
    /** The legend of the rendered state; a focused tab shows its own until focus leaves. */
    private String legend = "";

    DiscordCommunityDetailPanel(Context context, ConsoleMenuBackdrop backdrop,
                                DiscordCommunityView.Callback callback) {
        this.context = context;
        this.callback = callback;
        scroll = new LockableScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(32));
        detail = new LinearLayout(context);
        detail.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(detail, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        panel = new ConsolePanel(context, scroll, backdrop, false);
        panel.floatOnGlass(detail);
    }

    void showTabLegend() { panel.setLegend(context.getString(R.string.discord_community_legend_tabs)); }

    void restoreLegend() { panel.setLegend(legend); }

    private void setLegend(int text) {
        legend = context.getString(text);
        panel.setLegend(legend);
    }

    /**
     * Replaces the panel content for {@code selected} in the model's detail state. A rebuild of
     * the same screen (a refresh, a setting's result) keeps the scroll offset and the focused row.
     */
    void render(DiscordCommunityView.Model value,
                DiscordCommunityPresentation.Destination selected) {
        String screen = value == null ? "" : value.state.detail + "|" + (selected == null ? "" : selected.id);
        View focused = scroll.findFocus();
        Object focusedTag = focused == null ? null : focused.getTag();
        if (focused != null) {
            // Removing the focused row would hand focus to the first focusable on screen (a tab,
            // which switches tabs); the panel holds it until the row is rebuilt.
            scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            scroll.requestFocus();
        }
        build(value, selected);
        if (!screen.equals(renderedScreen)) scroll.scrollTo(0, 0);
        renderedScreen = screen;
        if (focused == null) return;
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View target = focusedTag instanceof String ? byTag.get(focusedTag) : null;
        if (target == null && focusedTag instanceof String) target = destinations.get(focusedTag);
        if (target == null) target = firstAction;
        if (target != null) target.requestFocus();
    }

    private void build(DiscordCommunityView.Model value,
                       DiscordCommunityPresentation.Destination selected) {
        model = value;
        detail.removeAllViews();
        destinations.clear();
        byTag.clear();
        focusables.clear();
        firstAction = null;
        if (model == null) return;
        DiscordCommunityState.Detail state = model.state.detail;
        if (selected == null) {
            if (!DiscordCommunityView.isLinked(model.snapshot)) {
                addConnect();
            } else if (model.homeUnavailableText != 0) {
                addHostSetup();
            } else {
                header(context.getString(R.string.discord_community_selected), "", null, null);
                setLegend(R.string.discord_community_legend_feed);
            }
            return;
        }
        setLegend(state == DiscordCommunityState.Detail.FEED
                ? R.string.discord_community_legend_feed : R.string.discord_community_legend_detail);
        if (selected.source instanceof HostGatewayClient.DiscordChannel
                && DiscordCommunityView.isConnectedChannel(selected.id, model.voice)) {
            // Voice state is authoritative even when the surrounding route was restored as FEED.
            addActiveVoiceDetail(selected);
            return;
        }
        header(context.getString(R.string.discord_community_selected), selected.title,
                subtitle(context, selected, model.voice), avatar(selected.title, selected.avatarUrl, 40));
        if (selected.participantCount > 0) {
            addNote(context.getString(R.string.discord_community_people,
                    selected.participantCount));
        }
        if (state == DiscordCommunityState.Detail.SERVER_CHANNELS) {
            addGuildChannels(selected);
        } else if (state == DiscordCommunityState.Detail.CHANNEL
                && selected.source instanceof HostGatewayClient.DiscordChannel) {
            addJoinAction((HostGatewayClient.DiscordChannel) selected.source);
        } else if (selected.kind == DiscordCommunityPresentation.Kind.FRIEND) {
            // Also in the list state (mockups 19-together/friends): ► reaches it from the row.
            int label = model.directMessagesCapable ? R.string.discord_dm_send_message
                    : model.directMessagesScopeAvailable ? R.string.discord_dm_chat_open_elsewhere
                    : R.string.discord_dm_upgrade;
            TextView message = row(context.getString(label), R.drawable.ic_console_chat);
            message.setTag("discord.community.friend.message");
            message.setOnClickListener(ignored -> {
                if (model.directMessagesCapable) callback.onOpenDirectMessage(selected);
                else if (!model.directMessagesScopeAvailable) callback.onUpgradeDirectMessages();
            });
            message.setEnabled(model.directMessagesCapable || !model.directMessagesScopeAvailable);
            firstAction = message;
            addAction(message);
        } else if (selected.kind == DiscordCommunityPresentation.Kind.SERVER) {
            firstAction = navigationRow(context.getString(R.string.discord_community_open), 0);
            firstAction.setOnClickListener(ignored -> callback.onDestinationOpened(selected));
            addAction(firstAction);
        } else if (selected.kind == DiscordCommunityPresentation.Kind.CHANNEL) {
            firstAction = row(context.getString(R.string.discord_community_join),
                    R.drawable.ic_console_headset);
            firstAction.setOnClickListener(ignored -> callback.onDestinationOpened(selected));
            addAction(firstAction);
        }
    }

    /** Mockup "discord_disconnected": no account and nothing selected. */
    private void addConnect() {
        header("Discord", context.getString(R.string.discord_community_connect_title),
                context.getString(R.string.discord_community_connect_details), null);
        TextView connect = row(context.getString(R.string.discord_social_connect_action),
                R.drawable.ic_console_link);
        connect.setTag("discord.community.social.connect");
        connect.setOnClickListener(ignored -> callback.onAuthorize());
        addAction(connect);
        TextView options = navigationRow(context.getString(R.string.discord_community_options),
                R.drawable.ic_console_settings);
        options.setTag("discord.community.connect.options");
        options.setOnClickListener(ignored -> callback.onOptions());
        addAction(options);
        setLegend(R.string.console_panel_legend);
    }

    /**
     * Servers and voice need Discord on the host: turned off here, not ready or not authorized
     * there. Settings › Discord turns it on and repairs or connects it.
     */
    private void addHostSetup() {
        header("Discord", context.getString(R.string.discord_community_host_setup_title),
                context.getString(R.string.discord_community_host_setup_details), null);
        TextView options = navigationRow(context.getString(R.string.discord_community_options),
                R.drawable.ic_console_settings);
        options.setTag("discord.community.host.options");
        options.setOnClickListener(ignored -> callback.onOptions());
        firstAction = options;
        addAction(options);
        setLegend(R.string.console_panel_legend);
    }

    private void addJoinAction(HostGatewayClient.DiscordChannel channel) {
        TextView joinAction = row(model.voiceBusy
                ? context.getString(R.string.discord_joining_channel)
                : context.getString(R.string.discord_join_channel, channel.name),
                R.drawable.ic_console_headset);
        joinAction.setTag("discord.community.join");
        joinAction.setOnClickListener(ignored -> {
            if (!model.voiceBusy) callback.onJoin(channel);
        });
        firstAction = joinAction;
        addAction(joinAction, model.voiceBusy);
    }

    /** Mockup "discord_voice": channel header, who is there, then the voice controls. */
    private void addActiveVoiceDetail(DiscordCommunityPresentation.Destination selected) {
        HostGatewayClient.DiscordVoice voice = model.voice;
        HostGatewayClient.DiscordChannel channel = selected.source instanceof HostGatewayClient.DiscordChannel
                ? (HostGatewayClient.DiscordChannel) selected.source : null;
        String guild = channel != null && !channel.guildName.isEmpty() ? channel.guildName
                : voice == null ? "" : voice.guildId;
        String connected = context.getString(R.string.discord_state_connected);
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(DiscordCommunityStyle.dot(DiscordCommunityStyle.BLURPLE, 0, 0));
        ImageView headset = new ImageView(context);
        headset.setImageResource(R.drawable.ic_console_headset);
        headset.setColorFilter(DiscordCommunityStyle.TEXT);
        badge.addView(headset, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        header(context.getString(R.string.discord_community_voice_channel), selected.title,
                guild.isEmpty() ? connected : guild + " · " + connected, badge);
        int people = voice == null ? selected.participantCount : voice.participants;
        TextView status = text(context.getString(R.string.discord_community_connected) + " · "
                + context.getString(R.string.discord_community_people, people), 13,
                DiscordCommunityStyle.ONLINE, false);
        GradientDrawable dot = DiscordCommunityStyle.dot(DiscordCommunityStyle.ONLINE, 0, 0);
        int dotSize = dp(6);
        int dotOffset = ConsoleText.dotOffset(status, dotSize);
        dot.setBounds(0, dotOffset, dotSize, dotSize + dotOffset);
        status.setCompoundDrawablesRelative(dot, null, null, null);
        status.setCompoundDrawablePadding(dp(6));
        status.setPadding(dp(ConsolePanel.ROW_INSET_DP), 0, dp(ConsolePanel.ROW_INSET_DP), 0);
        detail.addView(status, margins(MATCH_PARENT, WRAP_CONTENT, 0, 6));
        if (voice == null) return;
        if (voice.participantList.isEmpty()) {
            addNote(model.voiceLoading ? context.getString(R.string.discord_community_loading)
                    : context.getString(R.string.discord_no_participants));
        } else {
            for (HostGatewayClient.DiscordParticipant participant : voice.participantList) {
                addParticipant(participant);
            }
        }
        detail.addView(divider(), dividerParams());
        addVoiceAction(model.voice.muted ? R.string.overlay_discord_unmute : R.string.overlay_discord_mute,
                DiscordPanelController.CommunityVoiceAction.MUTE, R.drawable.ic_console_microphone);
        addVoiceAction(model.voice.deafened ? R.string.discord_enable_audio : R.string.discord_disable_audio,
                DiscordPanelController.CommunityVoiceAction.DEAFEN, R.drawable.ic_console_speaker);
        addVoiceAction(R.string.overlay_discord_leave, DiscordPanelController.CommunityVoiceAction.LEAVE,
                R.drawable.ic_console_leave);
    }

    /** 44 px avatar, a 3 px green ring while speaking, name with "ty"/"wyciszony". */
    private void addParticipant(HostGatewayClient.DiscordParticipant participant) {
        LinearLayout line = new LinearLayout(context);
        line.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ring = new FrameLayout(context);
        if (participant.speaking) {
            GradientDrawable outline = DiscordCommunityStyle.dot(0, DiscordCommunityStyle.ONLINE, dp(1.5f));
            ring.setBackground(outline);
        }
        ring.addView(avatar(participant.name, "", 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(25), dp(25));
        ringParams.leftMargin = dp(ConsolePanel.ROW_INSET_DP - 1.5f);
        line.addView(ring, ringParams);
        TextView name = text(participant.name
                + (participant.self ? context.getString(R.string.discord_you_suffix) : "")
                + (participant.muted ? context.getString(R.string.discord_muted_suffix) : ""),
                14, DiscordCommunityStyle.TEXT, false);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(6.5f);
        line.addView(name, nameParams);
        detail.addView(line, new LinearLayout.LayoutParams(MATCH_PARENT, dp(30)));
    }

    private void addVoiceAction(int label, DiscordPanelController.CommunityVoiceAction action, int icon) {
        TextView view = row(context.getString(label), icon);
        view.setTag("discord.community.voice." + action.name().toLowerCase(java.util.Locale.US));
        view.setOnClickListener(ignored -> {
            if (!model.voiceBusy) callback.onVoiceAction(action);
        });
        if (action == DiscordPanelController.CommunityVoiceAction.LEAVE) addDestructive(view, model.voiceBusy);
        else addAction(view, model.voiceBusy);
    }

    private void addGuildChannels(DiscordCommunityPresentation.Destination selected) {
        if (model.guildLoading) { addNote(context.getString(R.string.discord_community_loading)); return; }
        if (model.guildUnavailable) {
            addNote(context.getString(R.string.discord_community_host_unavailable));
            if (selected.kind == DiscordCommunityPresentation.Kind.SERVER) {
                firstAction = row(context.getString(R.string.discord_social_retry),
                        R.drawable.ic_console_pulse);
                firstAction.setOnClickListener(ignored -> callback.onDestinationOpened(selected));
                addAction(firstAction);
            }
            return;
        }
        for (DiscordCommunityPresentation.Destination channel : model.guildChannels) {
            addDetailChannel(channel);
        }
    }

    private void addDetailChannel(DiscordCommunityPresentation.Destination item) {
        TextView row = row(item.title, R.drawable.ic_console_headset);
        row.setTag(item.id);
        row.setContentDescription(item.title + ", " + subtitle(context, item, model.voice));
        row.setOnFocusChangeListener((ignored, focused) -> {
            if (focused) {
                DiscordCommunityStyle.reveal(scroll, row);
                callback.onDestinationFocused(item);
            }
        });
        row.setOnClickListener(ignored -> callback.onDestinationOpened(item));
        destinations.put(item.id, row);
        detail.addView(row, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        focusables.add(row);
    }

    private void addAction(View action) { addAction(action, false); }

    private void addAction(View action, boolean busy) {
        if (action.getId() == View.NO_ID) action.setId(View.generateViewId());
        action.setFocusableInTouchMode(true);
        if (busy) action.setAlpha(.58f);
        detail.addView(action, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        focusables.add(action);
        Object tag = action.getTag();
        if (!(tag instanceof String)) {
            tag = "discord.community.detail." + focusables.size();
            action.setTag(tag);
        }
        byTag.put((String) tag, action);
        if (firstAction == null) firstAction = action;
    }

    /** Destructive rows: red, last, behind a hairline (UX_MENU_PL.md 4.1). */
    private void addDestructive(TextView action, boolean busy) {
        detail.addView(divider(), dividerParams());
        addAction(panel.destructive(action), busy);
    }

    private void addNote(String value) {
        detail.addView(panel.note(value));
    }

    /** Panel header: eyebrow, then title and subtitle, beside an 80 px avatar when given. */
    private void header(String eyebrow, String title, String subtitle, View avatar) {
        header(eyebrow, title, subtitle, avatar, 12);
    }

    private void header(String eyebrow, String title, String subtitle, View avatar, int gapDp) {
        int inset = dp(ConsolePanel.ROW_INSET_DP);
        TextView eyebrowView = DiscordCommunityStyle.eyebrow(context, eyebrow);
        eyebrowView.setPadding(inset, 0, inset, 0);
        detail.addView(eyebrowView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        if (title.isEmpty()) return;
        TextView titleView = text(title, avatar == null ? 22 : 20, DiscordCommunityStyle.TEXT, true);
        titleView.setSingleLine(true); titleView.setEllipsize(TextUtils.TruncateAt.END);
        TextView subtitleView = TextUtils.isEmpty(subtitle) ? null
                : text(subtitle, 13, DiscordCommunityStyle.TEXT_SECONDARY, false);
        if (avatar == null) {
            titleView.setPadding(inset, 0, inset, 0);
            detail.addView(titleView, margins(MATCH_PARENT, WRAP_CONTENT, 4, subtitleView == null ? gapDp : 0));
            if (subtitleView != null) {
                subtitleView.setPadding(inset, 0, inset, 0);
                detail.addView(subtitleView, margins(MATCH_PARENT, WRAP_CONTENT, 3, gapDp));
            }
            return;
        }
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        avatarParams.leftMargin = dp(18);
        row.addView(avatar, avatarParams);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(titleView);
        if (subtitleView != null) words.addView(subtitleView, margins(MATCH_PARENT, WRAP_CONTENT, 3, 0));
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        wordsParams.leftMargin = dp(14);
        wordsParams.rightMargin = inset;
        row.addView(words, wordsParams);
        detail.addView(row, margins(MATCH_PARENT, WRAP_CONTENT, 17, 16));
    }

    /** A panel action row with an optional 15 dp line icon; untagged until the caller tags it. */
    private TextView row(String label, int icon) {
        TextView row = panel.action(label);
        row.setTag(null);
        if (icon != 0) iconize(row, icon);
        return row;
    }

    private TextView navigationRow(String label, int icon) {
        TextView row = panel.navigation(label);
        row.setTag(null);
        if (icon != 0) iconize(row, icon);
        return row;
    }

    /** Panel icons are 15 dp; a larger source (the top bar's gear) is drawn at that size. */
    private void iconize(TextView row, int icon) {
        panel.icon(row, icon);
        Drawable[] drawables = row.getCompoundDrawablesRelative();
        drawables[0].setBounds(0, 0, dp(15), dp(15));
        row.setCompoundDrawablesRelative(drawables[0], drawables[1], drawables[2], drawables[3]);
    }

    private View divider() {
        View divider = new View(context);
        divider.setBackgroundColor(0x1EFFFFFF);
        divider.setFocusable(false);
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, dp(1) / 2));
        params.setMargins(dp(ConsolePanel.ROW_INSET_DP), dp(6), dp(ConsolePanel.ROW_INSET_DP), dp(6));
        return params;
    }

    static String subtitle(Context context, DiscordCommunityPresentation.Destination item,
                           HostGatewayClient.DiscordVoice voice) {
        if (DiscordCommunityView.isConnectedChannel(item.id, voice)) {
            return context.getString(R.string.discord_community_connected);
        }
        return item.participantCount > 0 ? item.subtitle + " · " + context.getString(
                R.string.discord_community_people, item.participantCount) : item.subtitle;
    }
    private FrameLayout avatar(String title, String url, int sizeDp) {
        return DiscordCommunityPresentation.avatar(context, title, url, sizeDp);
    }

    private TextView text(CharSequence value, float size, int color, boolean medium) {
        return DiscordCommunityStyle.text(context, value, size, color, medium);
    }

    private LinearLayout.LayoutParams margins(int width, int height, int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(topDp);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private int dp(float value) { return DiscordCommunityStyle.dp(context, value); }

    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
}
