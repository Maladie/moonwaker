package com.limelight.console;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders held while the entry intro runs ({@link ConsoleIntroView#afterIntro}): one per kind,
 * the latest held, released together in the order each kind was first held.
 */
final class ConsoleHeldRenders {
    private final Map<String, Runnable> held = new LinkedHashMap<>();

    void hold(String kind, Runnable render) {
        held.put(kind, render);
    }

    /** Runs what is held, once; a render held while these run waits for the next release. */
    void release() {
        List<Runnable> renders = new ArrayList<>(held.values());
        held.clear();
        for (Runnable render : renders) render.run();
    }
}
