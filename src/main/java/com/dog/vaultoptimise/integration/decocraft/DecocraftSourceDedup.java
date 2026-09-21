package com.dog.vaultoptimise.integration.decocraft;

import com.razz.decocraft.models.bbmodel.BBModel;
import com.razz.decocraft.models.bbmodel.BBModelParts.Texture;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shares immutable texture sources only during Decocraft's synchronous server registration. */
public final class DecocraftSourceDedup {
    static final int MAX_VALUES = 8192;
    static final long MAX_CHARACTERS = 64L * 1024 * 1024;
    // Do not intern globally or retain the startup canonical map after registration.
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static volatile Map<String, Object> lastReceipt = Map.of("state", "not_started");

    private DecocraftSourceDedup() {}

    public static void begin() {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested Decocraft registration");
        CURRENT.set(new Session());
    }

    public static void process(BBModel model) {
        Session session = CURRENT.get();
        if (session == null || model == null) return;
        session.models++;
        if (model.textures != null) {
            for (Texture texture : model.textures) {
                if (texture == null || texture.source == null) continue;
                session.sources++;
                String source = texture.source;
                String canonical = session.values.get(source);
                if (canonical != null) {
                    if (canonical != source) {
                        texture.source = canonical;
                        session.rewritten++;
                    }
                } else if (session.values.size() < MAX_VALUES
                        && source.length() <= MAX_CHARACTERS - session.characters) {
                    session.values.put(source, source);
                    session.characters += source.length();
                } else {
                    session.capMisses++;
                }
            }
        }
    }

    public static Map<String, Object> finish() {
        Session session = CURRENT.get();
        if (session == null) throw new IllegalStateException("No Decocraft registration session");
        try {
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("state", "complete");
            receipt.put("models", session.models);
            receipt.put("sourceOccurrences", session.sources);
            receipt.put("canonicalValues", session.values.size());
            receipt.put("canonicalCharacters", session.characters);
            receipt.put("rewrittenReferences", session.rewritten);
            receipt.put("capMisses", session.capMisses);
            receipt.put("canonicalPoolRetained", false);
            lastReceipt = Map.copyOf(receipt);
            return lastReceipt;
        } finally {
            session.values.clear();
            CURRENT.remove();
        }
    }

    public static Map<String, Object> receipt() { return lastReceipt; }

    public static boolean sessionActive() {
        boolean active = CURRENT.get() != null;
        if (!active) CURRENT.remove();
        return active;
    }

    private static final class Session {
        final HashMap<String, String> values = new HashMap<>();
        int models;
        int sources;
        int rewritten;
        int capMisses;
        long characters;
    }
}
