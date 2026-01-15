package dev.kir.cubeswithoutborders.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.FramebufferManager;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.GlResourceManager;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public final class FramebufferUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedOnce = false;

    public static void beginWrite(Framebuffer framebuffer, boolean setViewport) {
        // NOP
    }

    public static void draw(Framebuffer source, Framebuffer window) {
        draw(source, window, false);
    }

    public static void draw(Framebuffer source, Framebuffer window, boolean letterbox) {
        GlTexture srcColor = (GlTexture)source.getColorAttachment();
        GlTexture dstColor = (GlTexture)window.getColorAttachment();

        int srcColorAttachment = srcColor.getGlId();
        int srcDepthAttachment = 0;
        int dstColorAttachment = dstColor.getGlId();
        int dstDepthAttachment = 0;

        GlBackend backend = (GlBackend)RenderSystem.getDevice();
        GlResourceManager resourceManager = (GlResourceManager)backend.createCommandEncoder();
        FramebufferManager framebufferManager = backend.getFramebufferManager();

        int srcWidth = source.textureWidth;
        int srcHeight = source.textureHeight;
        int dstWidth = window.textureWidth;
        int dstHeight = window.textureHeight;

        int dstX0, dstY0, dstX1, dstY1;

        if (letterbox) {
            // Calculate aspect-ratio-preserving destination rectangle
            float srcAspect = (float) srcWidth / srcHeight;
            float dstAspect = (float) dstWidth / dstHeight;

            int scaledWidth, scaledHeight;
            if (srcAspect > dstAspect) {
                // Source is wider - fit to width, letterbox top/bottom
                scaledWidth = dstWidth;
                scaledHeight = Math.round(dstWidth / srcAspect);
            } else {
                // Source is taller - fit to height, letterbox left/right
                scaledHeight = dstHeight;
                scaledWidth = Math.round(dstHeight * srcAspect);
            }

            // Center the image
            dstX0 = (dstWidth - scaledWidth) / 2;
            dstY0 = (dstHeight - scaledHeight) / 2;
            dstX1 = dstX0 + scaledWidth;
            dstY1 = dstY0 + scaledHeight;

            // Clear to black for letterbox bars
            framebufferManager.setupFramebuffer(resourceManager.temporaryFb2, dstColorAttachment, dstDepthAttachment, 0, 0);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            if (!loggedOnce) {
                LOGGER.info("[CWB] Letterbox enabled: rendering {}x{} to {}x{} (dst rect: {},{} -> {},{})",
                        srcWidth, srcHeight, dstWidth, dstHeight, dstX0, dstY0, dstX1, dstY1);
                loggedOnce = true;
            }
        } else {
            // Stretch to fill
            dstX0 = 0;
            dstY0 = 0;
            dstX1 = dstWidth;
            dstY1 = dstHeight;
        }

        framebufferManager.setupFramebuffer(resourceManager.temporaryFb1, srcColorAttachment, srcDepthAttachment, 0, 0);
        framebufferManager.setupFramebuffer(resourceManager.temporaryFb2, dstColorAttachment, dstDepthAttachment, 0, 0);
        framebufferManager.setupBlitFramebuffer(
                resourceManager.temporaryFb1, resourceManager.temporaryFb2,
                0, 0, srcWidth, srcHeight,
                dstX0, dstY0, dstX1, dstY1,
                GL12.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );
    }

    public static void resize(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null || framebuffer.textureWidth == width && framebuffer.textureHeight == height) {
            return;
        }

        framebuffer.resize(width, height);
    }

    public static void resetLogging() {
        loggedOnce = false;
    }

    private FramebufferUtil() { }
}
