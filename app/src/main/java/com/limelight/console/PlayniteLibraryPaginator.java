package com.limelight.console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure cursor paginator shared by the repository and local tests. */
final class PlayniteLibraryPaginator {
    interface Fetcher {
        HostGatewayClient.PlayniteLibrary fetch(String cursor, int limit) throws IOException;
    }

    interface Cancellation { boolean cancelled(); }

    static final class Result {
        final List<HostGatewayClient.PlayniteGame> games;
        final String revision;

        Result(List<HostGatewayClient.PlayniteGame> games, String revision) {
            this.games = games;
            this.revision = revision;
        }
    }

    static Result fetchAll(Fetcher fetcher, Cancellation cancellation,
                           int pageSize, int maxPages) throws IOException {
        Map<String, HostGatewayClient.PlayniteGame> games = new LinkedHashMap<>();
        String cursor = "";
        String revision = "";
        for (int page = 0; page < maxPages; page++) {
            if (cancellation.cancelled() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Playnite library request cancelled");
            }
            HostGatewayClient.PlayniteLibrary response = fetcher.fetch(cursor, pageSize);
            if (!response.revision.isEmpty()) revision = response.revision;
            for (HostGatewayClient.PlayniteGame game : response.games) games.put(game.id, game);
            String next = response.nextCursor == null ? "" : response.nextCursor;
            if (next.isEmpty()) {
                return new Result(new ArrayList<>(games.values()), revision);
            }
            if (next.equals(cursor)) throw new IOException("Invalid Playnite pagination cursor");
            cursor = next;
        }
        throw new IOException("Playnite pagination exceeded the safety limit");
    }

    private PlayniteLibraryPaginator() { }
}
