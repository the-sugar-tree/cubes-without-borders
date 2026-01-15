package dev.kir.cubeswithoutborders.client;

import com.mojang.logging.LogUtils;
import dev.kir.cubeswithoutborders.client.config.CubesWithoutBordersConfig;
import dev.kir.cubeswithoutborders.client.util.FramebufferUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Window;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public final class ResizableGameRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResizableGameRenderer INSTANCE;

    private final MinecraftClient client;

    private Framebuffer framebuffer;

    private Framebuffer clientFramebuffer;

    private int framebufferWidth;

    private int framebufferHeight;

    private int windowFramebufferWidth;

    private int windowFramebufferHeight;

    private boolean letterboxEnabled;

    private ResizableGameRenderer(MinecraftClient client) {
        this.client = client;
        this.framebuffer = null;
        this.clientFramebuffer = null;
        this.framebufferWidth = -1;
        this.framebufferHeight = -1;
        this.letterboxEnabled = false;
    }

    public static ResizableGameRenderer getInstance() {
        return ResizableGameRenderer.INSTANCE;
    }

    public boolean isEnabled() {
        return this.framebufferWidth > 0 && this.framebufferHeight > 0;
    }

    public boolean isRendering() {
        return this.clientFramebuffer != null;
    }

    public boolean isLetterboxEnabled() {
        return this.letterboxEnabled;
    }

    public int getRenderWidth() {
        return this.framebufferWidth;
    }

    public int getRenderHeight() {
        return this.framebufferHeight;
    }

    /**
     * Calculate the letterbox viewport rectangle in framebuffer coordinates.
     * @return {@code int[4]}: {x, y, width, height} or null if letterbox is not active.
     */
    public int[] getLetterboxViewport() {
        if (!this.letterboxEnabled || !this.isEnabled()) {
            return null;
        }

        Window window = this.client.getWindow();
        if (window == null) {
            return null;
        }

        int windowWidth = window.getFramebufferWidth();
        int windowHeight = window.getFramebufferHeight();
        int renderWidth = this.framebufferWidth;
        int renderHeight = this.framebufferHeight;

        float renderAspect = (float) renderWidth / renderHeight;
        float windowAspect = (float) windowWidth / windowHeight;

        int viewportWidth, viewportHeight, viewportX, viewportY;

        if (renderAspect > windowAspect) {
            // Render is wider - fit to width, letterbox top/bottom
            viewportWidth = windowWidth;
            viewportHeight = Math.round(windowWidth / renderAspect);
        } else {
            // Render is taller - fit to height, letterbox left/right
            viewportHeight = windowHeight;
            viewportWidth = Math.round(windowHeight * renderAspect);
        }

        viewportX = (windowWidth - viewportWidth) / 2;
        viewportY = (windowHeight - viewportHeight) / 2;

        return new int[]{viewportX, viewportY, viewportWidth, viewportHeight};
    }

    /**
     * Calculate the letterbox viewport rectangle in scaled (GUI) coordinates.
     * Use this for UI rendering with DrawContext coordinates.
     * @return {@code int[4]}: {x, y, width, height} or null if letterbox is not active.
     */
    public int[] getScaledLetterboxViewport(int scaledWindowWidth, int scaledWindowHeight) {
        if (!this.letterboxEnabled || !this.isEnabled()) {
            return null;
        }

        Window window = this.client.getWindow();
        if (window == null) {
            return null;
        }

        int windowWidth = window.getFramebufferWidth();
        int windowHeight = window.getFramebufferHeight();
        int renderWidth = this.framebufferWidth;
        int renderHeight = this.framebufferHeight;

        float renderAspect = (float) renderWidth / renderHeight;
        float windowAspect = (float) windowWidth / windowHeight;

        int scaledViewportWidth, scaledViewportHeight, scaledViewportX, scaledViewportY;

        if (renderAspect > windowAspect) {
            // Render is wider - fit to width, letterbox top/bottom
            scaledViewportWidth = scaledWindowWidth;
            scaledViewportHeight = Math.round(scaledWindowWidth / renderAspect);
        } else {
            // Render is taller - fit to height, letterbox left/right
            scaledViewportHeight = scaledWindowHeight;
            scaledViewportWidth = Math.round(scaledWindowHeight * renderAspect);
        }

        scaledViewportX = (scaledWindowWidth - scaledViewportWidth) / 2;
        scaledViewportY = (scaledWindowHeight - scaledViewportHeight) / 2;

        return new int[]{scaledViewportX, scaledViewportY, scaledViewportWidth, scaledViewportHeight};
    }

    public void resize(int width, int height) {
        this.resize(width, height, false);
    }

    public void resize(int width, int height, boolean letterbox) {
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.letterboxEnabled = letterbox;
        if (letterbox) {
            LOGGER.info("[CWB] ResizableGameRenderer: letterbox mode enabled, render size {}x{}", width, height);
            FramebufferUtil.resetLogging();
        }
        this.reload();
    }

    public void reload() {
        if (!this.isEnabled()) {
            return;
        }

        FramebufferUtil.resize(this.framebuffer, this.framebufferWidth, this.framebufferHeight);
        this.resizeWorldRendererFramebuffers();
    }

    public void disable() {
        this.framebufferWidth = -1;
        this.framebufferHeight = -1;
        this.letterboxEnabled = false;

        Window window = this.client.getWindow();
        if (window != null && this.windowFramebufferWidth > 0 && this.windowFramebufferHeight > 0) {
            window.framebufferWidth = this.windowFramebufferWidth;
            window.framebufferHeight = this.windowFramebufferHeight;
        }
        this.windowFramebufferWidth = -1;
        this.windowFramebufferHeight = -1;

        if (this.clientFramebuffer != null) {
            this.client.framebuffer = this.clientFramebuffer;
            FramebufferUtil.beginWrite(this.clientFramebuffer, true);
        }
        this.clientFramebuffer = null;

        if (this.framebuffer != null) {
            this.framebuffer.delete();
            this.framebuffer = null;
        }

        LOGGER.info("[CWB] ResizableGameRenderer disabled");
    }

    public void beginRender() {
        Window window = this.client.getWindow();
        if (window == null) {
            return;
        }

        // Auto-enable letterbox if config says so and we're in borderless fullscreen
        if (!this.isEnabled()) {
            this.tryAutoEnable();
        }

        if (!this.isEnabled()) {
            return;
        }

        int width = this.framebufferWidth;
        int height = this.framebufferHeight;

        if (this.framebuffer == null) {
            this.framebuffer = new WindowFramebuffer(width, height);

            // We need to manually trigger `initFbo` for
            // `FramebufferMixin` to do its thing.
            this.framebuffer.resize(width, height);
            this.resizeWorldRendererFramebuffers();
        }

        this.windowFramebufferWidth = window.framebufferWidth;
        this.windowFramebufferHeight = window.framebufferHeight;
        window.framebufferWidth = width;
        window.framebufferHeight = height;

        this.clientFramebuffer = this.client.getFramebuffer();
        this.client.framebuffer = this.framebuffer;
        FramebufferUtil.beginWrite(this.framebuffer, true);
    }

    /**
     * Automatically enable letterbox mode if config is enabled and we're in borderless fullscreen.
     * This handles cases like joining a world or server where the renderer wasn't initialized yet.
     */
    private void tryAutoEnable() {
        CubesWithoutBordersConfig config = CubesWithoutBordersConfig.getInstance();
        FullscreenManager manager = FullscreenManager.getInstance();

        // Only auto-enable if:
        // 1. Letterbox is enabled in config
        // 2. We're in borderless fullscreen mode
        // 3. Custom render dimensions are valid
        if (config.isLetterboxEnabled()
                && manager.getFullscreenMode() == FullscreenMode.BORDERLESS) {
            int customWidth = config.getCustomRenderWidth();
            int customHeight = config.getCustomRenderHeight();
            if (customWidth > 0 && customHeight > 0) {
                LOGGER.info("[CWB] Auto-enabling letterbox mode on world load");
                this.resize(customWidth, customHeight, true);
            }
        }
    }

    public void endRender() {
        Window window = this.client.getWindow();
        if (!this.isEnabled() || window == null) {
            return;
        }

        if (this.framebuffer == null || this.clientFramebuffer == null) {
            return;
        }

        window.framebufferWidth = this.windowFramebufferWidth;
        window.framebufferHeight = this.windowFramebufferHeight;
        this.windowFramebufferWidth = -1;
        this.windowFramebufferHeight = -1;

        this.client.framebuffer = this.clientFramebuffer;
        FramebufferUtil.beginWrite(this.clientFramebuffer, true);
        FramebufferUtil.draw(this.framebuffer, this.clientFramebuffer, this.letterboxEnabled);
        this.clientFramebuffer = null;
    }

    private void resizeWorldRendererFramebuffers() {
        int width = this.framebufferWidth;
        int height = this.framebufferHeight;
        Window window = this.client.getWindow();
        WorldRenderer worldRenderer = this.client.worldRenderer;
        if (window == null) {
            return;
        }

        FramebufferUtil.resize(worldRenderer.entityOutlineFramebuffer, width, height);
    }

    static {
        INSTANCE = new ResizableGameRenderer(MinecraftClient.getInstance());
    }
}
