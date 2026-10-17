package org.essupport.network;

import com.google.gson.Gson;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import org.essupport.server.SupportServer;

public final class SupportNetwork {
    private static final Gson JSON = new Gson();
    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    new ResourceLocation("essupport", "main"), () -> "1", "1"::equals, "1"::equals);

    public static void register() {
        CHANNEL.messageBuilder(SupportAction.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SupportAction::write)
                .decoder(SupportAction::read)
                .consumerMainThread(
                        (p, c) -> {
                            var player = c.get().getSender();
                            if (player != null) SupportServer.action(player, p);
                            c.get().setPacketHandled(true);
                        })
                .add();
        CHANNEL.messageBuilder(SupportSnapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, b) -> b.writeUtf(JSON.toJson(p), 262144))
                .decoder(b -> JSON.fromJson(b.readUtf(262144), SupportSnapshot.class))
                .consumerMainThread(
                        (p, c) -> {
                            DistExecutor.unsafeRunWhenOn(
                                    Dist.CLIENT,
                                    () -> () -> org.essupport.client.SupportClient.receive(p));
                            c.get().setPacketHandled(true);
                        })
                .add();
    }

    public static void send(ServerPlayer player, SupportSnapshot data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), data);
    }

    public static void send(SupportAction action) {
        CHANNEL.sendToServer(action);
    }
}
