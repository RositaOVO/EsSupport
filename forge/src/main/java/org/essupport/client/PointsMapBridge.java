package org.essupport.client;

import net.minecraft.client.gui.GuiGraphics;

import java.lang.reflect.*;

/**
 * Optional EsPoints integration. A projection mismatch disables picking instead of sending a wrong
 * target.
 */
final class PointsMapBridge {
    private Object hud, viewport;
    private Method render, contains, worldX, worldZ, screenX, screenZ, zoom;
    private Field viewportField;

    PointsMapBridge() {
        try {
            Class<?> type = Class.forName("com.example.espoints.hud.TacticalMapHUD");
            hud = type.getMethod("getInstance").invoke(null);
            render =
                    type.getMethod(
                            "renderEmbeddedMap",
                            GuiGraphics.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class);
            zoom = type.getMethod("zoomFromMouseWheel", double.class);
            viewportField = type.getDeclaredField("lastInteractiveViewport");
            viewportField.setAccessible(true);
            Class<?> v = viewportField.getType();
            contains = method(v, "containsScreen", double.class, double.class);
            worldX = method(v, "worldX", double.class);
            worldZ = method(v, "worldZ", double.class);
            screenX = method(v, "screenXd", double.class);
            screenZ = method(v, "screenYd", double.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            hud = null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... args)
            throws ReflectiveOperationException {
        Method m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    boolean render(GuiGraphics g, int x, int y, int w, int h, float p) {
        if (hud == null) return false;
        try {
            render.invoke(hud, g, x, y, w, h, p);
            viewport = viewportField.get(hud);
            return viewport != null;
        } catch (ReflectiveOperationException e) {
            viewport = null;
            return false;
        }
    }

    double[] pick(double x, double y) {
        try {
            if (viewport != null && (boolean) contains.invoke(viewport, x, y))
                return new double[] {
                    ((Number) worldX.invoke(viewport, x)).doubleValue(),
                    ((Number) worldZ.invoke(viewport, y)).doubleValue()
                };
        } catch (ReflectiveOperationException e) {
            viewport = null;
        }
        return null;
    }

    double[] project(double x, double z) {
        try {
            if (viewport != null)
                return new double[] {
                    ((Number) screenX.invoke(viewport, x)).doubleValue(),
                    ((Number) screenZ.invoke(viewport, z)).doubleValue()
                };
        } catch (ReflectiveOperationException e) {
            viewport = null;
        }
        return null;
    }

    void zoom(double d) {
        try {
            if (hud != null) zoom.invoke(hud, d);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
