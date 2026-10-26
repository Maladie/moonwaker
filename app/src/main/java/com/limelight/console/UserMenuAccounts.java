package com.limelight.console;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The user menu's "Connected accounts" (#47): the host's setup facts (#48) and the profile's
 * Windows sign-in readiness, each as one row state. The host owns every fact; a fact it could
 * not tell stays UNKNOWN, never a guess.
 */
final class UserMenuAccounts {
    /** Order is the row order. Each kind has its fix in the wizard's check texts. */
    enum Kind { STEAM, EPIC, DISCORD, WINDOWS_SIGN_IN }

    /** ATTENTION is recommended setup, OPTIONAL may stay undone (as in {@link FirstRunPlan}). */
    enum State { READY, ATTENTION, OPTIONAL, UNKNOWN }

    static final class Row {
        final Kind kind;
        final State state;
        /** The Epic account name when signed in; otherwise empty. */
        final String name;

        Row(Kind kind, State state, String name) {
            this.kind = kind;
            this.state = state;
            this.name = name;
        }
    }

    private static final Pattern HEX_32 =
            Pattern.compile("(?<![0-9A-Fa-f])[0-9A-Fa-f]{32}(?![0-9A-Fa-f])");

    private UserMenuAccounts() { }

    /**
     * The key or code inside whatever was pasted: Steam's page line ("Key: ..."), Epic's
     * whole JSON reply (its first 32-hex value is the authorization code) or the bare value.
     * Null when nothing fits; the host validates again.
     */
    static String pastedSecret(String pasted) {
        if (pasted == null) return null;
        Matcher match = HEX_32.matcher(pasted);
        return match.find() ? match.group() : null;
    }

    /** {@code setup} null: the host did not answer (or is not adult-facing). */
    /**
     * {@code remoteSignInGranted}: this TV may sign the profile in (its per-TV grant on the
     * host); without it the profile's own readiness does not make the row ready.
     */
    static List<Row> rows(HostGatewayClient.SetupFacts setup, boolean remoteSignInGranted,
                          String remoteSignInState) {
        String epicName = setup != null && Boolean.TRUE.equals(setup.epicSignedIn)
                ? setup.epicDisplayName : "";
        return Arrays.asList(
                new Row(Kind.STEAM, flag(setup == null ? null : setup.steamApiKeySaved,
                        State.ATTENTION), ""),
                new Row(Kind.EPIC, flag(setup == null ? null : setup.epicSignedIn,
                        State.OPTIONAL), epicName),
                new Row(Kind.DISCORD, discord(setup), ""),
                new Row(Kind.WINDOWS_SIGN_IN, remoteSignInGranted
                        ? windowsSignIn(remoteSignInState) : State.ATTENTION, ""));
    }

    private static State flag(Boolean value, State missing) {
        if (value == null) return State.UNKNOWN;
        return value ? State.READY : missing;
    }

    private static State discord(HostGatewayClient.SetupFacts setup) {
        if (setup == null) return State.UNKNOWN;
        if (HostGatewayClient.SetupFacts.DISCORD_LINKED.equals(setup.discordState)) {
            return State.READY;
        }
        return HostGatewayClient.SetupFacts.DISCORD_NOT_LINKED.equals(setup.discordState)
                ? State.OPTIONAL : State.UNKNOWN;
    }

    /** Gateway {@code remote_sign_in_state}: an unreadable broker is not a setup gap. */
    private static State windowsSignIn(String state) {
        if (state == null) return State.UNKNOWN;
        switch (state) {
            case "ready": return State.READY;
            case "disabled":
            case "action_required": return State.ATTENTION;
            default: return State.UNKNOWN;
        }
    }
}
