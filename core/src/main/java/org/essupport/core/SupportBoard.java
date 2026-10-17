package org.essupport.core;

import static org.essupport.core.SupportTypes.*;

import java.util.*;
import java.util.function.Predicate;

/** Server-thread state machine. No client may set completion. */
public final class SupportBoard {
    public static final class Denied extends RuntimeException {
        public Denied(String message) {
            super(message);
        }
    }

    private final LinkedHashMap<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, Long> lastCreated = new HashMap<>();
    private final CargoLedger cargo = new CargoLedger();
    private final Rules rules;
    private long session, revision;

    public SupportBoard(Rules rules) {
        this.rules = rules;
    }

    public long session() {
        return session;
    }

    public long revision() {
        return revision;
    }

    public void reset(long nextSession) {
        session = nextSession;
        orders.clear();
        lastCreated.clear();
        cargo.clear();
        revision++;
    }

    public Order get(UUID id) {
        return orders.get(id);
    }

    public List<Order> visible(Actor actor) {
        return actor.member()
                ? orders.values().stream().filter(o -> o.team().equals(actor.team())).toList()
                : List.of();
    }

    public Order createSupply(
            Actor actor, Radio radio, int ammo, int materials, String note, long tick) {
        require(actor.requester(), "仅已部署的小队长或指挥官可申请");
        require(radio != null && actor.team().equals(radio.team()), "请选择有效的己方电台");
        require(
                ammo >= 0
                        && materials >= 0
                        && ammo <= rules.maximumQuantity()
                        && materials <= rules.maximumQuantity()
                        && (ammo > 0 || materials > 0),
                "物资数量超出允许范围");
        require(
                orders.values().stream()
                        .noneMatch(
                                o ->
                                        o.state().active()
                                                && o.team().equals(actor.team())
                                                && radio.id().equals(o.radioId())),
                "该电台已有待完成订单");
        checkCreate(actor, tick);
        return insert(
                actor,
                radio.dimension(),
                radio.id(),
                radio.name(),
                radio.x(),
                radio.z(),
                ammo,
                materials,
                note,
                tick,
                rules.supplyLifetime());
    }

    private void checkCreate(Actor actor, long tick) {
        require(
                tick - lastCreated.getOrDefault(actor.id(), Long.MIN_VALUE / 2)
                        >= rules.createInterval(),
                "申请过于频繁，请稍后再试");
        long active =
                orders.values().stream()
                        .filter(o -> o.team().equals(actor.team()) && o.state().active())
                        .count();
        require(active < rules.maximumPerTeam(), "本方申请数量已达上限");
        while (orders.values().stream().filter(o -> o.team().equals(actor.team())).count()
                >= rules.maximumPerTeam()) {
            UUID old =
                    orders.values().stream()
                            .filter(o -> o.team().equals(actor.team()) && !o.state().active())
                            .map(Order::id)
                            .findFirst()
                            .orElseThrow(() -> new Denied("本方申请数量已达上限"));
            orders.remove(old);
        }
    }

    private Order insert(
            Actor actor,
            String dimension,
            UUID radio,
            String destination,
            double x,
            double z,
            int ammo,
            int materials,
            String note,
            long tick,
            long lifetime) {
        require(dimension != null && !dimension.isBlank(), "当前战场无效");
        Order order =
                new Order(
                        UUID.randomUUID(),
                        State.OPEN,
                        session,
                        actor.team(),
                        dimension,
                        radio,
                        destination,
                        x,
                        z,
                        ammo,
                        materials,
                        0,
                        0,
                        actor.id(),
                        actor.name(),
                        actor.squad(),
                        null,
                        "",
                        clean(note),
                        "",
                        tick,
                        tick + lifetime,
                        tick);
        orders.put(order.id(), order);
        lastCreated.put(actor.id(), tick);
        revision++;
        return order;
    }

    private Order owned(UUID id, Actor actor, long tick) {
        Order o = orders.get(id);
        require(
                o != null
                        && actor.member()
                        && o.team().equals(actor.team())
                        && o.session() == session,
                "申请不存在或无权访问");
        if (o.state().active() && tick >= o.expires()) {
            put(o.state(State.EXPIRED, "申请已过期", tick));
            throw new Denied("申请已过期");
        }
        return o;
    }

    public void claim(UUID id, Actor actor, long tick) {
        Order o = owned(id, actor, tick);
        require(actor.deployed() && actor.squad() >= 0, "请先加入小队并部署");
        require(o.state() == State.OPEN, "订单已被接取或已结束");
        require(
                orders.values().stream()
                        .noneMatch(
                                v -> v.state() == State.CLAIMED && actor.id().equals(v.assignee())),
                "请先完成或释放已接订单");
        put(o.claim(actor, tick));
    }

    public void release(UUID id, Actor actor, long tick) {
        Order o = owned(id, actor, tick);
        require(
                o.state() == State.CLAIMED
                        && (actor.commander() || actor.id().equals(o.assignee())),
                "只有接单者或指挥官可释放");
        put(o.release("订单已重新开放", tick));
    }

    public void cancel(UUID id, Actor actor, long tick) {
        Order o = owned(id, actor, tick);
        require(
                o.state().active() && (actor.commander() || actor.id().equals(o.requester())),
                "无权取消或该申请已执行");
        put(o.state(State.CANCELLED, "申请已取消", tick));
    }

    public void transfer(
            UUID vehicle,
            Actor actor,
            UUID radio,
            Resource resource,
            boolean loading,
            int amount,
            int before,
            long tick) {
        int eligible = cargo.transfer(vehicle, resource, radio, loading, amount, before);
        if (loading || eligible <= 0 || !actor.member() || !actor.deployed()) return;
        for (Order o : List.copyOf(orders.values())) {
            if (o.state() == State.CLAIMED
                    && tick < o.expires()
                    && o.team().equals(actor.team())
                    && actor.id().equals(o.assignee())
                    && Objects.equals(o.radioId(), radio)) {
                put(o.credit(resource, eligible, tick));
                break;
            }
        }
    }

    public void maintain(long tick, Predicate<Order> validRadio, Predicate<Order> validAssignee) {
        lastCreated.entrySet().removeIf(e -> tick - e.getValue() >= rules.createInterval());
        for (Order o : List.copyOf(orders.values())) {
            if (!o.state().active()) continue;
            if (tick >= o.expires()) put(o.state(State.EXPIRED, "申请已过期", tick));
            else if (!validRadio.test(o)) put(o.state(State.CANCELLED, "目标电台已失效", tick));
            else if (o.state() == State.CLAIMED && !validAssignee.test(o))
                put(o.release("接单者离线或已离开本方，订单重新开放", tick));
        }
    }

    public void closeRound(long tick) {
        for (Order o : List.copyOf(orders.values()))
            if (o.state().active()) put(o.state(State.CANCELLED, "本局已结束", tick));
        cargo.clear();
    }

    private void put(Order o) {
        orders.put(o.id(), o);
        revision++;
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new Denied(reason);
    }

    public static String clean(String text) {
        if (text == null) return "";
        String s = text.replaceAll("[\\p{Cntrl}§]", " ").strip();
        return s.length() > 96 ? s.substring(0, 96) : s;
    }
}
