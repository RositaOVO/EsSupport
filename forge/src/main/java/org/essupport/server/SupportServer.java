package org.essupport.server;

import static org.essupport.core.SupportTypes.*;

import com.google.gson.*;
import com.mojang.brigadier.Command;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.espetro.api.EspetroAPI;
import org.espetro.mapconfig.BattlefieldContext;
import org.espetro.team.*;
import org.essupport.SupportConfig;
import org.essupport.core.*;
import org.essupport.network.*;

import java.util.*;

@Mod.EventBusSubscriber(modid = "essupport")
public final class SupportServer {
    private static SupportBoard board;
    private static long clock;
    private static boolean wasBattle;
    private static final Map<UUID, Long> viewers = new HashMap<>(),
            lastPacket = new HashMap<>(),
            lastView = new HashMap<>();

    public static Actor actor(ServerPlayer p) {
        return new Actor(
                p.getUUID(),
                p.getGameProfile().getName(),
                EspetroAPI.getPlayerTeam(p),
                EspetroAPI.getPlayerSquadId(p.getUUID()),
                EspetroAPI.isSquadLeader(p.getUUID()),
                EspetroAPI.isCommander(p.getUUID()),
                EspetroAPI.isPlayerVisibleOnTacticalMap(p));
    }

    private static SupportBoard board() {
        long session = BattlefieldContext.getSessionId();
        if (board == null) {
            board = new SupportBoard(SupportConfig.rules());
            board.reset(session);
        } else if (board.session() != session) {
            board.reset(session);
            viewers.clear();
            wasBattle = false;
        }
        return board;
    }

    private static boolean battle() {
        return GameStateManager.getInstance().getCurrentPhase().name().equals("BATTLE");
    }

    private static String dimension() {
        return BattlefieldContext.getActiveDimensionKey()
                .map(k -> k.location().toString())
                .orElse("");
    }

    private static List<Radio> radios(Actor actor) {
        String dimension = dimension();
        return EspetroAPI.getFobs().stream()
                .filter(
                        f ->
                                actor.member()
                                        && actor.team().equals(f.team())
                                        && dimension.equals(f.dimension())
                                        && "RADIO".equalsIgnoreCase(f.type()))
                .limit(128)
                .map(
                        f ->
                                new Radio(
                                        f.id(),
                                        SupportBoard.clean(f.name()),
                                        f.team(),
                                        f.dimension(),
                                        f.x(),
                                        f.y(),
                                        f.z(),
                                        f.ammunition(),
                                        f.construction()))
                .toList();
    }

    private static Bounds bounds() {
        try {
            var snapshot = EspetroAPI.getActiveBattlefieldSnapshot().orElse(null);
            if (snapshot == null) return null;
            JsonObject j = JsonParser.parseString(snapshot.tacticalMapJson()).getAsJsonObject();
            double x = number(j, "topLeftX", -512),
                    z = number(j, "topLeftZ", -512),
                    xx = number(j, "bottomRightX", 512),
                    zz = number(j, "bottomRightZ", 512);
            if (Math.max(Math.max(Math.abs(x), Math.abs(z)), Math.max(Math.abs(xx), Math.abs(zz)))
                    > 29999984) return null;
            return new Bounds(Math.min(x, xx), Math.min(z, zz), Math.max(x, xx), Math.max(z, zz));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double number(JsonObject j, String key, double fallback) {
        return j.has(key) ? j.get(key).getAsDouble() : fallback;
    }

    public static void action(ServerPlayer player, SupportAction packet) {
        SupportBoard b = board();
        UUID uuid = player.getUUID();
        Map<UUID, Long> limiter = packet.type() == SupportAction.Type.VIEW ? lastView : lastPacket;
        long prior = limiter.getOrDefault(uuid, Long.MIN_VALUE / 2);
        if (clock - prior < (packet.type() == SupportAction.Type.VIEW ? 20 : 4)) return;
        limiter.put(uuid, clock);
        viewers.put(uuid, clock);
        Actor actor = actor(player);
        String message = "";
        try {
            if (packet.type() != SupportAction.Type.VIEW) {
                require(packet.session() == b.session(), "战局已改变，请重新打开支援面板");
                require(battle(), "只能在战斗阶段处理支援申请");
                Order created = null;
                switch (packet.type()) {
                    case SUPPLY ->
                            created =
                                    b.createSupply(
                                            actor,
                                            radios(actor).stream()
                                                    .filter(r -> r.id().equals(packet.id()))
                                                    .findFirst()
                                                    .orElse(null),
                                            packet.ammo(),
                                            packet.materials(),
                                            packet.note(),
                                            clock);
                    case CLAIM -> b.claim(packet.id(), actor, clock);
                    case RELEASE -> b.release(packet.id(), actor, clock);
                    case CANCEL -> b.cancel(packet.id(), actor, clock);
                    default -> {}
                }
                if (created != null && player.getServer() != null) {
                    String notice =
                            "[EsSupport] "
                                    + actor.name()
                                    + " 发布了"
                                    + "后勤订单："
                                    + created.destination()
                                    + "；按支援面板键查看（默认 O）。";
                    for (ServerPlayer member : player.getServer().getPlayerList().getPlayers())
                        if (actor.team().equals(EspetroAPI.getPlayerTeam(member)))
                            member.sendSystemMessage(Component.literal(notice));
                }
                message = "操作已完成";
            }
        } catch (SupportBoard.Denied e) {
            message = e.getMessage();
        }
        send(player, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new SupportBoard.Denied(message);
    }

    private static void send(ServerPlayer p, String message) {
        Actor a = actor(p);
        SupportNetwork.send(
                p,
                new SupportSnapshot(
                        board().session(),
                        board().revision(),
                        clock,
                        a,
                        battle(),
                        bounds(),
                        radios(a),
                        board().visible(a),
                        message));
    }

    public static void transfer(
            ServerPlayer p,
            UUID vehicle,
            UUID radio,
            Resource resource,
            boolean loading,
            int amount,
            int before) {
        if (!battle()) return;
        board().transfer(vehicle, actor(p), radio, resource, loading, amount, before, clock);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        clock++;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        SupportBoard b = board();
        boolean battle = battle();
        if (wasBattle && !battle) b.closeRound(clock);
        wasBattle = battle;
        if (clock % 20 != 0) return;
        b.maintain(
                clock,
                o ->
                        EspetroAPI.getFobs().stream()
                                .anyMatch(
                                        f ->
                                                f.id().equals(o.radioId())
                                                        && o.team().equals(f.team())
                                                        && o.dimension().equals(f.dimension())
                                                        && "RADIO".equalsIgnoreCase(f.type())),
                o -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(o.assignee());
                    return p != null && o.team().equals(actor(p).team()) && actor(p).squad() >= 0;
                });
        for (var it = viewers.entrySet().iterator(); it.hasNext(); ) {
            var v = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(v.getKey());
            if (p == null || clock - v.getValue() > 120) {
                it.remove();
                continue;
            }
            send(p, "");
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher()
                .register(
                        Commands.literal("essupport")
                                .executes(
                                        c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            viewers.put(p.getUUID(), clock);
                                            send(p, "OPEN");
                                            return Command.SINGLE_SUCCESS;
                                        }));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        viewers.remove(e.getEntity().getUUID());
        lastPacket.remove(e.getEntity().getUUID());
        lastView.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent e) {
        board = null;
        clock = 0;
        wasBattle = false;
        viewers.clear();
        lastPacket.clear();
        lastView.clear();
    }
}
