package org.essupport.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.essupport.network.*;
import org.lwjgl.glfw.GLFW;

public final class SupportClient {
    public static SupportSnapshot snapshot;
    private static int ticks;
    public static final KeyMapping OPEN =
            new KeyMapping(
                    "key.essupport.open",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    "key.categories.essupport");

    public static void open() {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.getConnection() == null
                || !SupportNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection()))
            return;
        mc.setScreen(new SupportScreen(mc.screen));
        refresh();
    }

    public static void refresh() {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null
                && SupportNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection()))
            SupportNetwork.send(
                    SupportAction.simple(
                            SupportAction.Type.VIEW,
                            snapshot == null ? 0 : snapshot.session(),
                            null));
    }

    public static void receive(SupportSnapshot data) {
        snapshot = data;
        var mc = Minecraft.getInstance();
        if ("OPEN".equals(data.message())) mc.setScreen(new SupportScreen(mc.screen));
        else if (mc.screen instanceof SupportScreen screen) screen.message(data.message());
    }

    @Mod.EventBusSubscriber(
            modid = "essupport",
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
        }
    }

    @Mod.EventBusSubscriber(modid = "essupport", value = Dist.CLIENT)
    public static class Events {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            while (OPEN.consumeClick()) open();
            if (++ticks % 40 == 0
                    && (Minecraft.getInstance().screen instanceof SupportScreen
                            || Minecraft.getInstance().screen instanceof LogisticsMapScreen))
                refresh();
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            snapshot = null;
            ticks = 0;
        }

        private static boolean deploy(net.minecraft.client.gui.screens.Screen s) {
            return s.getClass().getName().equals("org.espetro.client.gui.UnifiedDeployScreen");
        }

        @SubscribeEvent
        public static void render(ScreenEvent.Render.Post e) {
            if (!deploy(e.getScreen())) return;
            GuiGraphics g = e.getGuiGraphics();
            int x = e.getScreen().width - 94;
            g.fill(x, 6, x + 86, 25, 0xee263a3a);
            SupportScreen.text(g, "后勤订单 [O]", x + 8, 10, 0xffe4c774, 9, 70);
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
            if (e.getScreen() instanceof LogisticsMapScreen target) {
                target.mouseClicked(e.getMouseX(), e.getMouseY(), e.getButton());
                e.setCanceled(true);
                return;
            }
            if (deploy(e.getScreen())
                    && e.getButton() == 0
                    && e.getMouseX() >= e.getScreen().width - 94
                    && e.getMouseX() < e.getScreen().width - 8
                    && e.getMouseY() >= 6
                    && e.getMouseY() < 25) {
                e.setCanceled(true);
                open();
            }
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void scroll(ScreenEvent.MouseScrolled.Pre e) {
            if (e.getScreen() instanceof LogisticsMapScreen target) {
                target.mouseScrolled(e.getMouseX(), e.getMouseY(), e.getScrollDelta());
                e.setCanceled(true);
            }
        }
    }
}
