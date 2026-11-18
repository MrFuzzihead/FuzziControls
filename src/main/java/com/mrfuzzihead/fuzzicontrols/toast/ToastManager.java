package com.mrfuzzihead.fuzzicontrols.toast;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Queue and renderer for modern-style toasts in the top-right corner.
 *
 * <p>
 * This is a 1.7.10 implementation of the 1.12 {@code GuiToast} behaviour: up to five toasts are
 * visible at once, new toasts slide in from the right over 600 ms, stay for five seconds, and slide
 * back out. Additional toasts wait in a queue. The panel, animation timing and text placement
 * mirror the modern toast layout (160×32 panel, 16×16 icon at 8,8, title at 30,7 and subtitle at
 * 30,18).
 *
 * <p>
 * Rendering is driven by {@link ToastRenderer}. The queue is synchronized because a disconnect can
 * be detected from the render thread in {@code ControllerManager.pollFresh()}.
 */
public final class ToastManager {

    public static final int WIDTH = 160;

    public static final int HEIGHT = 32;

    public static final int MAX_VISIBLE = 5;

    static final long SLIDE_MILLIS = 600L;

    public static final ResourceLocation TOAST_TEXTURE = new ResourceLocation(
        FuzziControls.MODID,
        "textures/gui/toast.png");

    private static final ResourceLocation DUALSENSE_ICON = new ResourceLocation(
        FuzziControls.MODID,
        "textures/gui/controller_dualsense.png");

    private static final ResourceLocation XBOX_ICON = new ResourceLocation(
        FuzziControls.MODID,
        "textures/gui/controller_xbox.png");

    private static final ResourceLocation GENERIC_ICON = new ResourceLocation(
        FuzziControls.MODID,
        "textures/gui/controller_generic.png");

    private static final ToastManager INSTANCE = new ToastManager(System::currentTimeMillis);

    private final LongSupplier clock;

    private final Deque<Toast> queued = new ArrayDeque<>();

    private final ToastInstance[] visible = new ToastInstance[MAX_VISIBLE];

    ToastManager(LongSupplier clock) {
        this.clock = clock;
    }

    public static ToastManager getInstance() {
        return INSTANCE;
    }

    /** Queues a toast. Safe to call from the client tick or render thread. */
    public synchronized void add(Toast toast) {
        if (toast != null) queued.addLast(toast);
    }

    /** Drops every visible and queued toast. */
    public synchronized void clear() {
        queued.clear();
        Arrays.fill(visible, null);
    }

    /** Number of toasts waiting for a free slot; useful for diagnostics and the game test. */
    public synchronized int getQueuedToastCount() {
        return queued.size();
    }

    /** Number of toasts currently on screen; useful for diagnostics and the game test. */
    public synchronized int getVisibleToastCount() {
        int count = 0;
        for (ToastInstance instance : visible) {
            if (instance != null) count++;
        }
        return count;
    }

    /** Renders every visible toast in scaled GUI coordinates. */
    public synchronized void render(Minecraft mc, ScaledResolution resolution) {
        if (mc == null || resolution == null || mc.gameSettings == null || mc.gameSettings.hideGUI) return;

        long now = clock.getAsLong();
        updateSlots(now);

        int screenWidth = resolution.getScaledWidth();
        for (int slot = 0; slot < visible.length; slot++) {
            ToastInstance instance = visible[slot];
            if (instance == null) continue;
            drawToast(mc, instance, now, screenWidth, slot);
        }
    }

    /** Advances the queue/animation state without touching OpenGL. Used by unit tests. */
    synchronized void updateForTest(long now) {
        updateSlots(now);
    }

    ToastInstance getVisibleForTest(int slot) {
        return visible[slot];
    }

    private void updateSlots(long now) {
        for (int slot = 0; slot < visible.length; slot++) {
            if (visible[slot] == null) continue;
            visible[slot].update(now);
            if (visible[slot].isFinished(now)) {
                visible[slot] = null;
            }
        }

        for (int slot = 0; slot < visible.length; slot++) {
            if (visible[slot] == null && !queued.isEmpty()) {
                visible[slot] = new ToastInstance(queued.removeFirst(), now);
            }
        }
    }

    private static void drawToast(Minecraft mc, ToastInstance instance, long now, int screenWidth, int slot) {
        Toast toast = instance.getToast();
        int x = instance.getX(screenWidth, now);
        int y = slot * HEIGHT;

        // Isolate every bit of GL state this overlay touches. DrawScreenEvent.Post and
        // RenderGameOverlayEvent.Post run after other renderers, so leaking blend/depth/scissor
        // state here would affect whatever draws next.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            mc.getTextureManager()
                .bindTexture(TOAST_TEXTURE);
            ToastGui.INSTANCE.drawTexture(x, y, 0, 0, WIDTH, HEIGHT);

            mc.getTextureManager()
                .bindTexture(iconTexture(toast.getIcon()));
            ToastGui.INSTANCE.drawTexture(x + 8, y + 8, 0, 0, 16, 16);

            FontRenderer font = mc.fontRenderer;
            if (font != null) {
                String title = switch (toast.getType()) {
                    case CONNECTED -> I18n.format("fuzzicontrols.toast.connected");
                    case DISCONNECTED -> I18n.format("fuzzicontrols.toast.disconnected");
                    case LOW_BATTERY -> I18n.format("fuzzicontrols.toast.low_battery");
                };
                String subtitle = buildSubtitle(toast);

                int titleColor = switch (toast.getType()) {
                    case CONNECTED -> 0xFF55FF55;
                    case DISCONNECTED -> 0xFFFF5555;
                    case LOW_BATTERY -> 0xFFFFAA00;
                };
                font.drawString(font.trimStringToWidth(title, WIDTH - 38), x + 30, y + 7, titleColor);
                font.drawString(font.trimStringToWidth(subtitle, WIDTH - 38), x + 30, y + 18, 0xFFFFFFFF);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static ResourceLocation iconTexture(Toast.Icon icon) {
        if (icon == Toast.Icon.DUALSENSE) return DUALSENSE_ICON;
        if (icon == Toast.Icon.XBOX) return XBOX_ICON;
        return GENERIC_ICON;
    }

    private static String buildSubtitle(Toast toast) {
        String device = toast.getDeviceName();
        if (device.isEmpty()) {
            device = I18n.format("fuzzicontrols.toast.controller");
        }
        String transport = toast.getTransportName();
        String subtitle = transport.isEmpty() ? device : device + " (" + transport + ")";
        String detail = toast.getDetail();
        return detail.isEmpty() ? subtitle : subtitle + " - " + detail;
    }

    /** A {@link Gui} subclass only so the toast panel can use a high z-level. */
    private static final class ToastGui extends Gui {

        private static final ToastGui INSTANCE = new ToastGui();

        private ToastGui() {
            zLevel = 500.0F;
        }

        private void drawTexture(int x, int y, int u, int v, int width, int height) {
            drawTexturedModalRect(x, y, u, v, width, height);
        }
    }

    /** Animation/visibility state for one toast in a slot. */
    static final class ToastInstance {

        private final Toast toast;

        private final long startTime;

        private long lastVisibleTime;

        private long hideStartTime = -1L;

        private boolean hiding;

        ToastInstance(Toast toast, long startTime) {
            this.toast = toast;
            this.startTime = startTime;
            this.lastVisibleTime = startTime;
        }

        Toast getToast() {
            return toast;
        }

        void update(long now) {
            if (hiding || now < startTime) return;

            // The five-second duration starts after the slide-in finishes, matching GuiToast.
            if (now - startTime <= SLIDE_MILLIS) {
                lastVisibleTime = now;
                return;
            }

            if (now - lastVisibleTime >= toast.getDurationMillis()) {
                hiding = true;
                hideStartTime = now;
            }
        }

        boolean isFinished(long now) {
            return hiding && now - hideStartTime >= SLIDE_MILLIS;
        }

        float getSlide(long now) {
            long transitionStart = hiding ? hideStartTime : startTime;
            float progress = (now - transitionStart) / (float) SLIDE_MILLIS;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            progress *= progress;
            return hiding ? 1.0F - progress : progress;
        }

        int getX(int screenWidth, long now) {
            return screenWidth - Math.round(WIDTH * getSlide(now));
        }
    }
}
