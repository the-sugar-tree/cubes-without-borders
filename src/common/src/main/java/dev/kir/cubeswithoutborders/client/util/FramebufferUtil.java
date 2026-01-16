package dev.kir.cubeswithoutborders.client.util;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public final class FramebufferUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedOnce = false;

    // Temporary FBOs for blitting - created once and reused
    private static int tempReadFbo = -1;
    private static int tempDrawFbo = -1;

    public static void beginWrite(Framebuffer framebuffer, boolean setViewport) {
        // NOP - not needed in new rendering pipeline
    }

    public static void draw(Framebuffer source, Framebuffer dest) {
        draw(source, dest, false);
    }

    public static void draw(Framebuffer source, Framebuffer dest, boolean letterbox) {
        GpuTexture srcTexture = source.getColorAttachment();
        GpuTexture dstTexture = dest.getColorAttachment();

        if (srcTexture == null || dstTexture == null) {
            LOGGER.warn("[CWB] Cannot blit: source or destination texture is null");
            return;
        }

        if (!(srcTexture instanceof GlTexture) || !(dstTexture instanceof GlTexture)) {
            LOGGER.warn("[CWB] Cannot blit: textures are not GlTexture instances");
            return;
        }

        int srcTextureId = ((GlTexture) srcTexture).getGlId();
        int dstTextureId = ((GlTexture) dstTexture).getGlId();

        int srcWidth = source.textureWidth;
        int srcHeight = source.textureHeight;
        int dstWidth = dest.textureWidth;
        int dstHeight = dest.textureHeight;

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

        // Initialize temporary FBOs if needed
        if (tempReadFbo == -1) {
            tempReadFbo = GL30.glGenFramebuffers();
            tempDrawFbo = GL30.glGenFramebuffers();
        }

        // Setup source FBO
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, tempReadFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcTextureId, 0);

        // Setup destination FBO
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, tempDrawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, dstTextureId, 0);

        // Clear destination to black if letterboxing (for the black bars)
        if (letterbox) {
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        // Perform the blit
        GL30.glBlitFramebuffer(
                0, 0, srcWidth, srcHeight,
                dstX0, dstY0, dstX1, dstY1,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );

        // Unbind FBOs
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
    }

    public static void resize(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null || (framebuffer.textureWidth == width && framebuffer.textureHeight == height)) {
            return;
        }

        framebuffer.resize(width, height);
    }

    public static void resetLogging() {
        loggedOnce = false;
    }

    public static void cleanup() {
        if (tempReadFbo != -1) {
            GL30.glDeleteFramebuffers(tempReadFbo);
            GL30.glDeleteFramebuffers(tempDrawFbo);
            tempReadFbo = -1;
            tempDrawFbo = -1;
        }
    }

    private FramebufferUtil() { }
}
