package dev.kir.cubeswithoutborders.client.mixin;

import dev.kir.cubeswithoutborders.client.ResizableGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void beginRender(CallbackInfo ci) {
        ResizableGameRenderer.getInstance().beginRender();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void endRender(CallbackInfo ci) {
        ResizableGameRenderer renderer = ResizableGameRenderer.getInstance();
        if (renderer.isLetterboxEnabled() && renderer.isEnabled()) {
            // In letterbox mode, draw entity outlines to the custom framebuffer BEFORE endRender()
            // This way the outlines are included in the letterbox blit
            this.client.worldRenderer.drawEntityOutlinesFramebuffer();
        }
        renderer.endRender();
    }

    /**
     * Redirect drawEntityOutlinesFramebuffer() call in render() method.
     * In letterbox mode, we already called it before endRender(), so skip it here.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V"))
    private void redirectDrawEntityOutlines(WorldRenderer worldRenderer) {
        ResizableGameRenderer renderer = ResizableGameRenderer.getInstance();
        if (renderer.isLetterboxEnabled() && renderer.isEnabled()) {
            // Already handled in endRender injection - skip
            return;
        }
        // Normal mode - call the original method
        worldRenderer.drawEntityOutlinesFramebuffer();
    }
}
