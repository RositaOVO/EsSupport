package org.essupport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.esradial.client.RadialUiText;
import org.essupport.core.SupportTypes.*;

/** Read-only destination map: cannot invoke artillery or marker creation. */
public final class LogisticsMapScreen extends Screen {
    private final Screen parent;
    private final Order order;
    private final Bounds bounds;
    private final PointsMapBridge bridge = new PointsMapBridge();
    private boolean mapped;

    LogisticsMapScreen(Screen parent, Order order, Bounds bounds) {
        super(Component.literal("订单目的地"));
        this.parent = parent;
        this.order = order;
        this.bounds = bounds;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        RadialUiText.beginFrame();
        g.fill(0, 0, width, height, 0xf515231e);
        SupportScreen.text(
                g,
                order.destination()
                        + " · X "
                        + Math.round(order.x())
                        + " / Z "
                        + Math.round(order.z()),
                18,
                14,
                0xffe2c777,
                11,
                width - 110);
        SupportScreen.text(g, "返回", width - 60, 14, 0xffe4ebdc, 11, 45);
        int x = 18, y = 46, w = width - 36, h = height - 86;
        g.fill(x, y, x + w, y + h, 0xff243b30);
        mapped = bridge.render(g, x, y, w, h, partial);
        if (!mapped) {
            g.fill(x, y, x + w, y + h, 0xff243b30);
            for (int i = 1; i < 8; i++) {
                g.fill(x + i * w / 8, y, x + i * w / 8 + 1, y + h, 0xff476050);
                g.fill(x, y + i * h / 8, x + w, y + i * h / 8 + 1, 0xff476050);
            }
            SupportScreen.text(g, "坐标视图 · 未连接 ESPoints 地图", x + 8, y + 8, 0xffb5c7b1, 9, w - 16);
        }
        double[] point =
                mapped
                        ? bridge.project(order.x(), order.z())
                        : new double[] {
                            x + (order.x() - bounds.minX()) / (bounds.maxX() - bounds.minX()) * w,
                            y + (order.z() - bounds.minZ()) / (bounds.maxZ() - bounds.minZ()) * h
                        };
        if (point != null
                && point[0] >= x
                && point[0] < x + w
                && point[1] >= y
                && point[1] < y + h) {
            int a = (int) point[0], b = (int) point[1];
            g.fill(Math.max(x, a - 6), b - 1, Math.min(x + w, a + 7), b + 2, 0xffe2c777);
            g.fill(a - 1, Math.max(y, b - 6), a + 2, Math.min(y + h, b + 7), 0xffe2c777);
        }
        SupportScreen.text(g, "目的地仅供定位 · 实际卸货在指定电台进行", 18, height - 26, 0xffa7bda6, 10, width - 36);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x > width - 85 && y < 40) onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (mapped) bridge.zoom(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
