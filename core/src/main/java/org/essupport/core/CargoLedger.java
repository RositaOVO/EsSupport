package org.essupport.core;

import static org.essupport.core.SupportTypes.*;

import java.util.*;

/** Tracks cargo provenance, not inventory. Only Espetro changes real supplies. */
public final class CargoLedger {
    private record Key(UUID vehicle, Resource resource) {}

    private record Lot(UUID source, int quantity, boolean verifiable) {}

    private final Map<Key, Deque<Lot>> cargo = new LinkedHashMap<>();
    private static final int MAX_VEHICLES_RESOURCES = 4096, MAX_LOTS = 128;

    public void clear() {
        cargo.clear();
    }

    /** Unknown initial cargo is not credited: load at a base/other Radio after startup. */
    public int transfer(
            UUID vehicle, Resource resource, UUID radio, boolean loading, int amount, int before) {
        if (vehicle == null || resource == null || amount <= 0 || before < 0) return 0;
        Key key = new Key(vehicle, resource);
        if (loading && amount > Integer.MAX_VALUE - before) {
            cargo.remove(key);
            return 0;
        }
        if (!cargo.containsKey(key) && cargo.size() >= MAX_VEHICLES_RESOURCES)
            cargo.remove(cargo.keySet().iterator().next());
        Deque<Lot> lots = cargo.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        int known = lots.stream().mapToInt(Lot::quantity).sum();
        if (known > before) consume(lots, known - before, null, false);
        else if (known < before) lots.addLast(new Lot(null, before - known, false));
        if (loading) {
            if (lots.size() >= MAX_LOTS) {
                int total = lots.stream().mapToInt(Lot::quantity).sum();
                lots.clear();
                lots.add(new Lot(null, total, false));
            }
            lots.addLast(new Lot(radio, amount, true));
            return 0;
        }
        return consume(lots, amount, radio, true);
    }

    private int consume(Deque<Lot> lots, int amount, UUID destination, boolean credit) {
        int eligible = 0;
        while (amount > 0 && !lots.isEmpty()) {
            Lot lot = lots.removeFirst();
            int used = Math.min(amount, lot.quantity());
            amount -= used;
            if (credit
                    && destination != null
                    && lot.verifiable()
                    && !destination.equals(lot.source())) eligible += used;
            if (used < lot.quantity())
                lots.addFirst(new Lot(lot.source(), lot.quantity() - used, lot.verifiable()));
        }
        return eligible;
    }
}
