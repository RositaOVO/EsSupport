package org.essupport.core;

import java.util.UUID;

public final class SupportTypes {
    private SupportTypes() {}

    public enum Resource {
        AMMO,
        MATERIALS
    }

    public enum State {
        OPEN,
        CLAIMED,
        COMPLETED,
        CANCELLED,
        EXPIRED;

        public boolean active() {
            return this == OPEN || this == CLAIMED;
        }
    }

    public record Actor(
            UUID id,
            String name,
            String team,
            int squad,
            boolean leader,
            boolean commander,
            boolean deployed) {
        public boolean member() {
            return id != null && ("ATTACK".equals(team) || "DEFEND".equals(team));
        }

        public boolean requester() {
            return member() && deployed && (leader || commander);
        }
    }

    public record Radio(
            UUID id,
            String name,
            String team,
            String dimension,
            int x,
            int y,
            int z,
            int ammo,
            int materials) {}

    public record Bounds(double minX, double minZ, double maxX, double maxZ) {
        public Bounds {
            if (!Double.isFinite(minX)
                    || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX)
                    || !Double.isFinite(maxZ)
                    || maxX <= minX
                    || maxZ <= minZ) throw new IllegalArgumentException("地图范围无效");
        }

        public boolean contains(double x, double z) {
            return Double.isFinite(x)
                    && Double.isFinite(z)
                    && x >= minX
                    && x < maxX
                    && z >= minZ
                    && z < maxZ;
        }
    }

    public record Rules(
            int maximumPerTeam, int maximumQuantity, long createInterval, long supplyLifetime) {
        public Rules {
            if (maximumPerTeam < 1
                    || maximumPerTeam > 96
                    || maximumQuantity < 1
                    || maximumQuantity > 100000
                    || createInterval < 0
                    || supplyLifetime < 20) throw new IllegalArgumentException("支援配置超出范围");
        }

        public static Rules defaults() {
            return new Rules(48, 5000, 200, 36000);
        }
    }

    public record Order(
            UUID id,
            State state,
            long session,
            String team,
            String dimension,
            UUID radioId,
            String destination,
            double x,
            double z,
            int ammo,
            int materials,
            int deliveredAmmo,
            int deliveredMaterials,
            UUID requester,
            String requesterName,
            int squad,
            UUID assignee,
            String assigneeName,
            String note,
            String result,
            long created,
            long expires,
            long changed) {
        public Order state(State next, String message, long tick) {
            return copy(
                    next, deliveredAmmo, deliveredMaterials, assignee, assigneeName, message, tick);
        }

        public Order claim(Actor actor, long tick) {
            return copy(
                    State.CLAIMED,
                    deliveredAmmo,
                    deliveredMaterials,
                    actor.id(),
                    actor.name(),
                    "",
                    tick);
        }

        public Order release(String reason, long tick) {
            return copy(State.OPEN, deliveredAmmo, deliveredMaterials, null, "", reason, tick);
        }

        public Order credit(Resource resource, int quantity, long tick) {
            int a =
                    resource == Resource.AMMO
                            ? (int) Math.min(ammo, (long) deliveredAmmo + quantity)
                            : deliveredAmmo;
            int m =
                    resource == Resource.MATERIALS
                            ? (int) Math.min(materials, (long) deliveredMaterials + quantity)
                            : deliveredMaterials;
            State next = a >= ammo && m >= materials ? State.COMPLETED : State.CLAIMED;
            return copy(
                    next,
                    a,
                    m,
                    assignee,
                    assigneeName,
                    next == State.COMPLETED ? "实际交付已完成" : "",
                    tick);
        }

        private Order copy(
                State next, int a, int m, UUID owner, String ownerName, String result, long tick) {
            return new Order(
                    id,
                    next,
                    session,
                    team,
                    dimension,
                    radioId,
                    destination,
                    x,
                    z,
                    ammo,
                    materials,
                    a,
                    m,
                    requester,
                    requesterName,
                    squad,
                    owner,
                    ownerName,
                    note,
                    result,
                    created,
                    expires,
                    tick);
        }
    }
}
