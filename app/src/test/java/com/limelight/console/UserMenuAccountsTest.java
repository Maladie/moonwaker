package com.limelight.console;

import static org.junit.Assert.assertEquals;

import com.limelight.console.UserMenuAccounts.Kind;
import com.limelight.console.UserMenuAccounts.Row;
import com.limelight.console.UserMenuAccounts.State;

import org.junit.Test;

import java.util.List;

public class UserMenuAccountsTest {
    @Test
    public void readySetupShowsEveryAccountReadyWithTheEpicName() {
        List<Row> rows = UserMenuAccounts.rows(new HostGatewayClient.SetupFacts(
                true, true, true, "ania.k", HostGatewayClient.SetupFacts.DISCORD_LINKED), true, "ready");
        assertEquals(Kind.STEAM, rows.get(0).kind);
        for (Row row : rows) assertEquals(State.READY, row.state);
        assertEquals("ania.k", rows.get(1).name);
    }

    @Test
    public void gapsUseTheWizardsWeight() {
        List<Row> rows = UserMenuAccounts.rows(new HostGatewayClient.SetupFacts(
                false, false, false, "stale", HostGatewayClient.SetupFacts.DISCORD_NOT_LINKED),
                true, "disabled");
        assertEquals(State.ATTENTION, rows.get(0).state);
        assertEquals(State.OPTIONAL, rows.get(1).state);
        assertEquals("", rows.get(1).name);
        assertEquals(State.OPTIONAL, rows.get(2).state);
        assertEquals(State.ATTENTION, rows.get(3).state);
    }

    @Test
    public void aReadyProfileWithoutThisTvsGrantIsNotReady() {
        assertEquals(State.ATTENTION, UserMenuAccounts.rows(null, false, "ready").get(3).state);
    }

    @Test
    public void pastedTextGivesTheKeyOrCodeInside() {
        String code = "0123456789abcdef0123456789abcdef";
        assertEquals(code, UserMenuAccounts.pastedSecret(
                "{\"warning\":\"Do not share\",\"redirectUrl\":\"https://localhost/launcher/"
                        + "authorized?code=" + code + "\",\"authorizationCode\":\"" + code
                        + "\",\"exchangeCode\":null,\"sid\":null}"));
        assertEquals("0123456789ABCDEF0123456789ABCDEF", UserMenuAccounts.pastedSecret(
                " Key: 0123456789ABCDEF0123456789ABCDEF \n"));
        assertEquals(null, UserMenuAccounts.pastedSecret("0123456789abcdef0123456789abcdef0"));
        assertEquals(null, UserMenuAccounts.pastedSecret("nope"));
    }

    @Test
    public void factsTheHostCouldNotTellStayUnknown() {
        for (Row row : UserMenuAccounts.rows(null, true, null)) assertEquals(State.UNKNOWN, row.state);
        List<Row> rows = UserMenuAccounts.rows(new HostGatewayClient.SetupFacts(
                null, null, null, "", HostGatewayClient.SetupFacts.DISCORD_UNKNOWN),
                true, "broker_unavailable");
        for (Row row : rows) assertEquals(State.UNKNOWN, row.state);
    }
}
