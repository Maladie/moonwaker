package com.limelight.console;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.SleepDeviceAdmin;
import com.limelight.ui.overlay.CustomCommand;
import com.limelight.ui.overlay.CustomCommandsManager;
import com.limelight.ui.overlay.OverlayIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Settings › Controllers and devices › Menu commands: the list of custom overlay commands and the
 * editor of one, on the console settings screen. The editor edits a draft; Save writes it to
 * {@link CustomCommandsManager}, Back drops it. The name is typed on the MoonWaker keyboard.
 */
final class ConsoleCustomCommands {
    private static final int[] KEY_CODES = {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INSERT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_BREAK
    };
    private static final int[] POST_ACTION_LABELS = {
            R.string.editor_then_nothing, R.string.editor_then_close_menu,
            R.string.editor_then_disconnect, R.string.editor_then_quit, R.string.editor_then_sleep
    };

    /** The command being added or edited; {@code id} is null until it is saved. */
    private static final class Draft {
        String id;
        String name = "";
        int icon = OverlayIcons.getDefaultIcon();
        boolean ctrl;
        boolean alt;
        boolean shift;
        boolean meta;
        int keyCode;
        int postAction = CustomCommand.POST_ACTION_NONE;

        static Draft of(CustomCommand command) {
            Draft draft = new Draft();
            draft.id = command.getId();
            draft.name = command.getName().equals(command.getKeyCombination().toDisplayString())
                    ? "" : command.getName();
            draft.icon = command.getIconResId();
            CustomCommand.KeyCombination keys = command.getKeyCombination();
            draft.ctrl = keys.isCtrl();
            draft.alt = keys.isAlt();
            draft.shift = keys.isShift();
            draft.meta = keys.isMeta();
            draft.keyCode = keys.getKeyCode();
            draft.postAction = command.getPostAction();
            return draft;
        }

        CustomCommand.KeyCombination keys() {
            return new CustomCommand.KeyCombination(ctrl, alt, shift, meta, keyCode);
        }
    }

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final CustomCommandsManager commands;

    ConsoleCustomCommands(Activity activity, ConsoleSettingsScreen screen) {
        this.activity = activity;
        this.screen = screen;
        commands = new CustomCommandsManager(activity);
    }

    /** Pushes the command list over Settings › Controllers and devices. */
    void open() {
        pushScreen(s(R.string.title_custom_commands), this::listRows);
    }

    private View[] listRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        TextView add = panel.navigation(s(R.string.custom_commands_add));
        add.setOnClickListener(view -> edit(new Draft()));
        rows.add(add);
        List<CustomCommand> saved = commands.getCommands();
        if (saved.isEmpty()) rows.add(panel.note(s(R.string.custom_commands_empty)));
        for (CustomCommand command : saved) {
            LinearLayout row = panel.value(command.getName(),
                    command.getKeyCombination().toDisplayString());
            row.setOnClickListener(view -> edit(Draft.of(command)));
            rows.add(row);
        }
        return rows.toArray(new View[0]);
    }

    private void edit(Draft draft) {
        pushScreen(s(draft.id == null ? R.string.editor_title_add : R.string.editor_title_edit),
                panel -> editorRows(panel, draft));
    }

    private void pushScreen(String title, java.util.function.Function<ConsolePanel, View[]> rows) {
        screen.push(s(R.string.console_settings_title) + "  ›  "
                        + s(R.string.console_settings_controllers), title,
                s(R.string.console_settings_controllers),
                java.util.Collections.singletonList(
                        new ConsoleSettingsScreen.Category(title, rows)), 0);
    }

    private View[] editorRows(ConsolePanel panel, Draft draft) {
        List<View> rows = new ArrayList<>();
        String combination = draft.keyCode == 0 ? "" : draft.keys().toDisplayString();
        LinearLayout name = panel.value(s(R.string.editor_command_name),
                draft.name.isEmpty() ? combination : draft.name);
        name.setOnClickListener(view -> editName(draft));
        rows.add(name);
        LinearLayout icon = panel.value(s(R.string.editor_icon),
                OverlayIcons.getIconName(draft.icon));
        icon.setOnClickListener(view -> chooseIcon(panel, draft));
        rows.add(icon);

        rows.add(panel.section(s(R.string.editor_modifiers)));
        rows.add(modifier(panel, R.string.editor_modifier_ctrl, draft.ctrl, on -> draft.ctrl = on));
        rows.add(modifier(panel, R.string.editor_modifier_alt, draft.alt, on -> draft.alt = on));
        rows.add(modifier(panel, R.string.editor_modifier_shift, draft.shift,
                on -> draft.shift = on));
        rows.add(modifier(panel, R.string.editor_modifier_meta, draft.meta, on -> draft.meta = on));

        LinearLayout key = panel.value(s(R.string.editor_key_code), draft.keyCode == 0
                ? s(R.string.editor_key_code_hint) : CustomCommand.KeyCombination.keyName(draft.keyCode));
        key.setOnClickListener(view -> chooseKey(draft));
        rows.add(key);
        LinearLayout then = panel.value(s(R.string.editor_then),
                s(POST_ACTION_LABELS[draft.postAction]));
        then.setOnClickListener(view -> chooseThen(draft));
        rows.add(then);

        TextView save = panel.action(s(R.string.editor_save));
        save.setOnClickListener(view -> save(draft));
        rows.add(save);
        if (draft.id != null) {
            int position = position(draft.id);
            if (position > 0) rows.add(move(panel, R.string.custom_commands_move_up, position, -1));
            if (position < commands.getCommands().size() - 1) {
                rows.add(move(panel, R.string.custom_commands_move_down, position, 1));
            }
            TextView delete = panel.destructive(panel.action(s(R.string.custom_commands_delete)));
            delete.setOnClickListener(view -> ConsoleConfirmDialog.show(activity,
                    s(R.string.custom_commands_delete), s(R.string.custom_commands_delete_confirm),
                    s(R.string.no), s(R.string.yes), () -> {
                        commands.removeCommand(draft.id);
                        notifyInfo(R.string.custom_commands_deleted);
                        screen.pop();
                    }));
            rows.add(delete);
        }
        return rows.toArray(new View[0]);
    }

    private LinearLayout modifier(ConsolePanel panel, int label, boolean checked,
                                  java.util.function.Consumer<Boolean> set) {
        LinearLayout row = panel.toggle(s(label), checked);
        boolean[] on = {checked};
        row.setOnClickListener(view -> {
            on[0] = !on[0];
            set.accept(on[0]);
            panel.setChecked(row, on[0]);
        });
        return row;
    }

    private TextView move(ConsolePanel panel, int label, int position, int direction) {
        TextView row = panel.action(s(label));
        row.setOnClickListener(view -> {
            commands.moveCommand(position, position + direction);
            screen.refresh();
        });
        return row;
    }

    private int position(String id) {
        List<CustomCommand> saved = commands.getCommands();
        for (int index = 0; index < saved.size(); index++) {
            if (saved.get(index).getId().equals(id)) return index;
        }
        return -1;
    }

    private void editName(Draft draft) {
        ConsoleUiFeedback feedback = ConsoleUiFeedback.find(activity);
        if (feedback == null) return;
        feedback.showInput(s(R.string.editor_command_name), null, draft.name,
                draft.keyCode == 0 ? null : draft.keys().toDisplayString(), false, 40,
                s(android.R.string.cancel), null, null, s(android.R.string.ok), value -> {
                    draft.name = value;
                    screen.refresh();
                    return null;
                }, null);
    }

    private void chooseIcon(ConsolePanel panel, Draft draft) {
        OverlayIcons.IconOption[] icons = OverlayIcons.AVAILABLE_ICONS;
        View[] rows = new View[icons.length];
        int current = 0;
        for (int index = 0; index < icons.length; index++) {
            OverlayIcons.IconOption option = icons[index];
            boolean chosen = option.resourceId == draft.icon;
            if (chosen) current = index;
            TextView row = panel.icon(panel.choice(option.name, chosen, false), option.resourceId);
            row.setOnClickListener(view -> {
                draft.icon = option.resourceId;
                screen.closePage();
            });
            rows[index] = row;
        }
        screen.showPage(s(R.string.editor_icon_select), "", rows, current);
    }

    private void chooseKey(Draft draft) {
        CharSequence[] names = new CharSequence[KEY_CODES.length];
        int current = -1;
        for (int index = 0; index < KEY_CODES.length; index++) {
            names[index] = CustomCommand.KeyCombination.keyName(KEY_CODES[index]);
            if (KEY_CODES[index] == draft.keyCode) current = index;
        }
        screen.showChoices(s(R.string.editor_key_code), "", names, current,
                index -> draft.keyCode = KEY_CODES[index]);
    }

    private void chooseThen(Draft draft) {
        CharSequence[] names = new CharSequence[POST_ACTION_LABELS.length];
        for (int index = 0; index < names.length; index++) names[index] = s(POST_ACTION_LABELS[index]);
        screen.showChoices(s(R.string.editor_then), "", names, draft.postAction,
                index -> draft.postAction = index);
    }

    private void save(Draft draft) {
        if (draft.keyCode == 0) {
            ConsoleUiFeedback feedback = ConsoleUiFeedback.find(activity);
            if (feedback != null) {
                feedback.notify(ConsoleUiFeedback.Kind.ERROR, s(R.string.editor_error_key_empty));
            }
            return;
        }
        CustomCommand command = new CustomCommand(
                draft.id == null ? UUID.randomUUID().toString() : draft.id, draft.name.trim(),
                draft.icon, draft.keys(), draft.postAction);
        if (draft.id == null) commands.addCommand(command);
        else commands.updateCommand(draft.id, command);
        if (draft.postAction == CustomCommand.POST_ACTION_SLEEP) requestSleepAdmin();
        notifyInfo(R.string.editor_command_saved);
        screen.pop();
    }

    /** The Sleep action locks the screen, which needs the device-admin grant. */
    private void requestSleepAdmin() {
        DevicePolicyManager policy = (DevicePolicyManager) activity.getSystemService(
                android.content.Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(activity, SleepDeviceAdmin.class);
        if (policy.isAdminActive(admin)) return;
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                s(R.string.sleep_admin_explanation));
        activity.startActivity(intent);
    }

    private void notifyInfo(int message) {
        ConsoleUiFeedback.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
