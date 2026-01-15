package dev.kir.cubeswithoutborders.client.mixin;

import dev.kir.cubeswithoutborders.client.ResizableGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
abstract class ScreenMixin {
    @Shadow
    public int width;

    @Shadow
    public int height;

    /**
     * Replace renderInGameBackground to draw the dark translucent gradient
     * only within the letterbox viewport area, not covering the black bars.
     */
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void renderInGameBackgroundLetterbox(DrawContext context, CallbackInfo ci) {
        ResizableGameRenderer renderer = ResizableGameRenderer.getInstance();
        int[] viewport = renderer.getScaledLetterboxViewport(this.width, this.height);

        if (viewport != null) {
            // Draw gradient only in the letterbox viewport area
            int x = viewport[0];
            int y = viewport[1];
            int w = viewport[2];
            int h = viewport[3];

            // Original colors from Screen.renderInGameBackground
            int colorStart = -1072689136; // 0xC0101010
            int colorEnd = -804253680;    // 0xD0101010

            context.fillGradient(x, y, x + w, y + h, colorStart, colorEnd);
            ci.cancel();
        }
        // If letterbox is not active, let the original method run
    }
}
