package org.essupport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.esradial.client.RadialUiText;
import org.essupport.core.SupportTypes.*;
import org.essupport.network.*;

import java.util.*;

/** AUI typography, responsive order cards and actual-delivery progress. */
public final class SupportScreen extends Screen {
    private final Screen parent;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double a, double b) {
            return a >= x && a < x + w && b >= y && b < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private int left, top, w, h, split, page, radioIndex, filter;
    private double scale = 1;
    private boolean form;
    private UUID selected;
    private long session = Long.MIN_VALUE, messageUntil;
    private String notice = "";
    private EditBox ammo, materials, note;

    public SupportScreen(Screen parent) {
        super(Component.literal("后勤订单"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String a = ammo == null ? "100" : ammo.getValue(),
                m = materials == null ? "100" : materials.getValue(),
                n = note == null ? "" : note.getValue();
        scale = Math.min(1, Math.min(width / 820.0, height / 480.0));
        int vw = (int) (width / scale), vh = (int) (height / scale);
        w = Math.min(vw - 16, 1000);
        h = Math.min(vh - 16, 540);
        left = (vw - w) / 2;
        top = (vh - h) / 2;
        split = left + (int) (w * .34);
        int rx = split + 18, rw = left + w - rx - 18;
        ammo = edit(rx, top + 181, (rw - 10) / 2, a, 6, true);
        materials = edit(rx + (rw + 10) / 2, top + 181, (rw - 10) / 2, m, 6, true);
        note = edit(rx, top + 239, rw, n, 96, false);
        inputs();
    }

    private EditBox edit(int x, int y, int w, String value, int max, boolean numeric) {
        EditBox e = new EditBox(font, x, y, w, 22, Component.empty());
        e.setMaxLength(max);
        e.setValue(value);
        e.setTextColor(0xffe5ede0);
        if (numeric) e.setFilter(s -> s.matches("[0-9]*"));
        addRenderableWidget(e);
        return e;
    }

    private void inputs() {
        ammo.visible = materials.visible = note.visible = form;
    }

    public void message(String s) {
        if (s != null && !s.isBlank()) {
            notice = s;
            messageUntil = System.currentTimeMillis() + 5000;
        }
    }

    public static void text(
            GuiGraphics g, String s, double x, double y, int color, double size, double max) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        RadialUiText.draw(g, RadialUiText.fit(s, max, size, 400), x, y, color, size, 400);
        com.sighs.apricityui.render.ImageDrawer.flushBatch();
    }

    private void t(GuiGraphics g, String s, int x, int y, int color, int size, int max) {
        text(g, s, x, y, color, size, max);
    }

    private void button(
            GuiGraphics g,
            String s,
            int x,
            int y,
            int width,
            boolean enabled,
            boolean primary,
            Runnable action,
            int mx,
            int my) {
        boolean hover = mx >= x && mx < x + width && my >= y && my < y + 25;
        g.fill(
                x,
                y,
                x + width,
                y + 25,
                !enabled ? 0xff29382f : primary ? 0xffe2c777 : hover ? 0xff496251 : 0xff30483c);
        t(
                g,
                s,
                x + 9,
                y + 6,
                !enabled ? 0xff7e9582 : primary ? 0xff1b2a20 : 0xffe5ebdf,
                11,
                width - 18);
        if (enabled) hits.add(new Hit(x, y, width, 25, action));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        g.pose().pushPose();
        g.pose().scale((float) scale, (float) scale, 1);
        try {
            draw(g, (int) (mx / scale), (int) (my / scale), p);
        } finally {
            g.flush();
            g.pose().popPose();
        }
    }

    private void draw(GuiGraphics g, int mx, int my, float p) {
        hits.clear();
        RadialUiText.beginFrame();
        g.fill(0, 0, (int) (width / scale), (int) (height / scale), 0xd00d1915);
        g.fill(left, top, left + w, top + h, 0xf81b2a26);
        g.fill(left, top, left + w, top + 2, 0xffe2c777);
        t(g, "ESSUPPORT / 后勤订单", left + 18, top + 14, 0xffedf0e5, 16, w - 120);
        button(g, "关闭", left + w - 72, top + 10, 54, true, false, this::onClose, mx, my);
        var d = SupportClient.snapshot;
        if (d == null) {
            t(g, "正在同步本方后勤订单…", left + 18, top + 80, 0xffb5c6b4, 12, w - 36);
            return;
        }
        if (session != d.session()) {
            session = d.session();
            selected = null;
            page = 0;
        }
        button(
                g,
                "全部订单",
                left + 18,
                top + 50,
                100,
                true,
                filter == 0,
                () -> {
                    filter = 0;
                    page = 0;
                },
                mx,
                my);
        button(
                g,
                "我接取的",
                left + 128,
                top + 50,
                110,
                true,
                filter == 1,
                () -> {
                    filter = 1;
                    page = 0;
                },
                mx,
                my);
        button(
                g,
                "新建订单",
                left + w - 138,
                top + 50,
                120,
                d.battle() && d.actor().requester(),
                true,
                () -> {
                    form = true;
                    inputs();
                },
                mx,
                my);
        g.fill(split, top + 94, split + 1, top + h - 40, 0xff40564a);
        int lx = left + 18, lw = split - left - 36;
        List<Order> all =
                d.orders().stream()
                        .filter(o -> filter == 0 || d.actor().id().equals(o.assignee()))
                        .sorted(Comparator.comparing(Order::created).reversed())
                        .toList();
        int rows = Math.max(1, (h - 179) / 65), pages = Math.max(1, (all.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = page * rows; i < Math.min(all.size(), (page + 1) * rows); i++) {
            Order o = all.get(i);
            int yy = top + 104 + (i - page * rows) * 65;
            g.fill(lx, yy, lx + lw, yy + 58, o.id().equals(selected) ? 0xff344d41 : 0xff233730);
            t(g, o.destination(), lx + 10, yy + 7, 0xffedf0e5, 12, lw - 20);
            t(
                    g,
                    status(o.state())
                            + " · "
                            + o.deliveredAmmo()
                            + "/"
                            + o.ammo()
                            + " 弹药 · "
                            + o.deliveredMaterials()
                            + "/"
                            + o.materials()
                            + " 建材",
                    lx + 10,
                    yy + 34,
                    0xffb2c3ae,
                    9,
                    lw - 20);
            hits.add(
                    new Hit(
                            lx,
                            yy,
                            lw,
                            58,
                            () -> {
                                selected = o.id();
                                form = false;
                                inputs();
                            }));
        }
        if (all.isEmpty()) t(g, "暂无后勤订单", lx + 8, top + 119, 0xffaebfac, 12, lw - 16);
        button(g, "‹", lx, top + h - 77, 30, page > 0, false, () -> page--, mx, my);
        t(g, (page + 1) + " / " + pages, lx + 41, top + h - 70, 0xffaebfac, 10, 70);
        button(
                g,
                "›",
                lx + lw - 30,
                top + h - 77,
                30,
                page + 1 < pages,
                false,
                () -> page++,
                mx,
                my);
        int rx = split + 18, rw = left + w - rx - 18;
        if (form) form(g, d, rx, rw, mx, my);
        else {
            Order o = all.stream().filter(v -> v.id().equals(selected)).findFirst().orElse(null);
            if (o == null) {
                t(g, "选择一条订单查看交付进度", rx, top + 113, 0xffe6ecde, 14, rw);
                t(g, "实际卸货后自动累计；可分批交付。", rx, top + 145, 0xff9eb2a3, 11, rw);
            } else detail(g, d, o, rx, rw, mx, my);
        }
        g.fill(left + 18, top + h - 34, left + w - 18, top + h - 33, 0xff40564a);
        t(
                g,
                System.currentTimeMillis() < messageUntil
                        ? notice
                        : !d.battle() ? "非战斗阶段，暂不可处理订单" : "本方可见 · 只统计接单者在指定电台的真实卸货量",
                left + 18,
                top + h - 23,
                0xffb8c9b5,
                10,
                w - 36);
        super.render(g, mx, my, p);
    }

    private void form(GuiGraphics g, SupportSnapshot d, int rx, int rw, int mx, int my) {
        t(g, "发布后勤订单", rx, top + 104, 0xffedf0e5, 15, rw);
        var radios = d.radios();
        if (!radios.isEmpty()) radioIndex = Math.floorMod(radioIndex, radios.size());
        Radio radio = radios.isEmpty() ? null : radios.get(radioIndex);
        button(
                g,
                radio == null ? "没有可用的己方电台" : radio.name() + "  ›",
                rx,
                top + 128,
                rw,
                radio != null,
                false,
                () -> radioIndex++,
                mx,
                my);
        t(g, "弹药数量", rx, top + 164, 0xff9eb2a3, 10, rw / 2 - 8);
        t(g, "建材数量", rx + (rw + 10) / 2, top + 164, 0xff9eb2a3, 10, rw / 2 - 8);
        t(g, "任务说明（可选）", rx, top + 221, 0xff9eb2a3, 10, rw);
        if (radio != null) {
            t(
                    g,
                    "电台库存：弹药 " + radio.ammo() + " / 建材 " + radio.materials(),
                    rx,
                    top + 282,
                    0xffb8c9b5,
                    11,
                    rw);
            t(g, "坐标：X " + radio.x() + " / Z " + radio.z(), rx, top + 309, 0xff9eb2a3, 10, rw);
        }
        t(g, "发布后等待队员接单运输。", rx, top + 344, 0xffe2c777, 11, rw);
        button(
                g,
                "返回订单",
                rx,
                top + h - 78,
                120,
                true,
                false,
                () -> {
                    form = false;
                    inputs();
                },
                mx,
                my);
        button(
                g,
                "发布订单",
                rx + rw - 150,
                top + h - 78,
                150,
                d.battle() && d.actor().requester() && radio != null,
                true,
                () -> {
                    try {
                        send(
                                new SupportAction(
                                        SupportAction.Type.SUPPLY,
                                        d.session(),
                                        radio.id(),
                                        Integer.parseInt(ammo.getValue()),
                                        Integer.parseInt(materials.getValue()),
                                        note.getValue()));
                    } catch (NumberFormatException e) {
                        message("请填写有效数量");
                    }
                },
                mx,
                my);
    }

    private void detail(GuiGraphics g, SupportSnapshot d, Order o, int rx, int rw, int mx, int my) {
        t(g, o.destination() + " · " + status(o.state()), rx, top + 106, 0xffedf0e5, 15, rw);
        t(
                g,
                "申请："
                        + o.requesterName()
                        + " / 小队 "
                        + o.squad()
                        + " · 接单："
                        + (o.assignee() == null ? "等待运输" : o.assigneeName()),
                rx,
                top + 139,
                0xffaebfac,
                10,
                rw);
        progress(g, "弹药", o.deliveredAmmo(), o.ammo(), rx, top + 183, rw);
        progress(g, "建材", o.deliveredMaterials(), o.materials(), rx, top + 246, rw);
        t(g, "运输要求", rx, top + 316, 0xff9eb2a3, 10, rw);
        t(g, o.note(), rx, top + 341, 0xffd3deca, 11, rw);
        t(g, o.result(), rx, top + 366, 0xff9eb2a3, 10, rw);
        int yy = top + h - 78, third = (rw - 12) / 3;
        if (o.state() == State.OPEN)
            button(
                    g,
                    "接取订单",
                    rx,
                    yy,
                    third,
                    d.battle() && d.actor().deployed() && d.actor().squad() >= 0,
                    true,
                    () -> simple(SupportAction.Type.CLAIM, o),
                    mx,
                    my);
        else
            button(
                    g,
                    "释放订单",
                    rx,
                    yy,
                    third,
                    d.battle()
                            && o.state() == State.CLAIMED
                            && (d.actor().commander() || d.actor().id().equals(o.assignee())),
                    false,
                    () -> simple(SupportAction.Type.RELEASE, o),
                    mx,
                    my);
        button(
                g,
                "取消订单",
                rx + third + 6,
                yy,
                third,
                d.battle()
                        && o.state().active()
                        && (d.actor().commander() || d.actor().id().equals(o.requester())),
                false,
                () -> simple(SupportAction.Type.CANCEL, o),
                mx,
                my);
        button(
                g,
                "定位目的地",
                rx + 2 * (third + 6),
                yy,
                third,
                d.bounds() != null,
                true,
                () -> minecraft.setScreen(new LogisticsMapScreen(this, o, d.bounds())),
                mx,
                my);
    }

    private void progress(
            GuiGraphics g, String label, int current, int target, int x, int y, int width) {
        t(g, label, x, y, 0xffe4ebdb, 12, width / 2);
        t(g, current + " / " + target, x + width / 2, y, 0xffe2c777, 12, width / 2);
        g.fill(x, y + 27, x + width, y + 34, 0xff14251b);
        int used = target == 0 ? width : (int) (width * Math.min(1, current / (double) target));
        g.fill(x, y + 27, x + used, y + 34, 0xffe2c777);
    }

    private void simple(SupportAction.Type type, Order o) {
        send(SupportAction.simple(type, o.session(), o.id()));
    }

    private void send(SupportAction action) {
        SupportNetwork.send(action);
        message("已提交，等待服务器确认…");
    }

    static String status(State s) {
        return switch (s) {
            case OPEN -> "待接单";
            case CLAIMED -> "运输中";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case EXPIRED -> "已过期";
        };
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        x /= scale;
        y /= scale;
        if (button == 0)
            for (Hit hit : List.copyOf(hits))
                if (hit.contains(x, y)) {
                    hit.action().run();
                    return true;
                }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / scale, y / scale, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / scale, y / scale, button, dx / scale, dy / scale);
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
