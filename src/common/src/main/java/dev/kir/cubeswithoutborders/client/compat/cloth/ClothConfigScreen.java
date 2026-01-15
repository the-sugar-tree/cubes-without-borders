package dev.kir.cubeswithoutborders.client.compat.cloth;

import dev.kir.cubeswithoutborders.client.FullscreenManager;
import dev.kir.cubeswithoutborders.client.FullscreenMode;
import dev.kir.cubeswithoutborders.client.FullscreenType;
import dev.kir.cubeswithoutborders.client.FullscreenTypes;
import dev.kir.cubeswithoutborders.client.ResizableGameRenderer;
import dev.kir.cubeswithoutborders.client.config.CubesWithoutBordersConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class ClothConfigScreen {
    public static Screen create(CubesWithoutBordersConfig config, String modId, Screen parent) {
        Text title = Text.translatable("modmenu.nameTranslation." + modId);
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(title);

        MinecraftClient client = MinecraftClient.getInstance();
        ConfigCategory category = builder.getOrCreateCategory(title);
        ConfigEntryBuilder entries = builder.entryBuilder();

        // Pause On Lost Focus
        Text pauseOnLostFocusText = Text.translatable("options.pauseOnLostFocus");
        boolean currentPauseOnLostFocus = client.options.pauseOnLostFocus;
        category.addEntry(entries
            .startBooleanToggle(pauseOnLostFocusText, currentPauseOnLostFocus)
            .setDefaultValue(true)
            .setSaveConsumer(x -> client.options.pauseOnLostFocus = x)
            .build());

        // Fullscreen Type
        Text fullscreenTypeText = Text.translatable("options.fullscreenType");
        FullscreenType defaultFullscreenType = FullscreenTypes.exclusive();
        FullscreenType currentFullscreenType = FullscreenTypes.validate(config.getFullscreenType(), defaultFullscreenType);
        List<String> fullscreenTypeSelections = FullscreenTypes.stream().map(FullscreenType::id).collect(Collectors.toList());
        category.addEntry(entries
            .startStringDropdownMenu(fullscreenTypeText, currentFullscreenType.id())
            .requireRestart() // This is a lie
            .setSuggestionMode(false)
            .setDefaultValue(defaultFullscreenType.id())
            .setSelections(fullscreenTypeSelections)
            .setSaveConsumer(x -> FullscreenTypes.get(x).ifPresent(config::setFullscreenType))
            .build());

        // Borderless Fullscreen Type
        Text borderlessFullscreenTypeText = Text.translatable("options.borderlessFullscreenType");
        FullscreenType defaultBorderlessFullscreenType = FullscreenTypes.borderless();
        FullscreenType currentBorderlessFullscreenType = FullscreenTypes.validate(config.getBorderlessFullscreenType(), defaultBorderlessFullscreenType);
        category.addEntry(entries
            .startStringDropdownMenu(borderlessFullscreenTypeText, currentBorderlessFullscreenType.id())
            .requireRestart() // This is a lie
            .setSuggestionMode(false)
            .setDefaultValue(defaultBorderlessFullscreenType.id())
            .setSelections(fullscreenTypeSelections)
            .setSaveConsumer(x -> FullscreenTypes.get(x).ifPresent(config::setBorderlessFullscreenType))
            .build());

        // Letterbox Mode
        Text letterboxEnabledText = Text.translatable("options.letterboxEnabled");
        boolean currentLetterboxEnabled = config.isLetterboxEnabled();
        category.addEntry(entries
            .startBooleanToggle(letterboxEnabledText, currentLetterboxEnabled)
            .setDefaultValue(false)
            .setTooltip(Text.translatable("options.letterboxEnabled.tooltip"))
            .setSaveConsumer(config::setLetterboxEnabled)
            .build());

        // Custom Render Width
        Text customRenderWidthText = Text.translatable("options.customRenderWidth");
        int currentCustomRenderWidth = config.getCustomRenderWidth();
        category.addEntry(entries
            .startIntField(customRenderWidthText, currentCustomRenderWidth)
            .setDefaultValue(2560)
            .setMin(640)
            .setMax(7680)
            .setTooltip(Text.translatable("options.customRenderWidth.tooltip"))
            .setSaveConsumer(config::setCustomRenderWidth)
            .build());

        // Custom Render Height
        Text customRenderHeightText = Text.translatable("options.customRenderHeight");
        int currentCustomRenderHeight = config.getCustomRenderHeight();
        category.addEntry(entries
            .startIntField(customRenderHeightText, currentCustomRenderHeight)
            .setDefaultValue(1440)
            .setMin(480)
            .setMax(4320)
            .setTooltip(Text.translatable("options.customRenderHeight.tooltip"))
            .setSaveConsumer(config::setCustomRenderHeight)
            .build());

        builder.setSavingRunnable(() -> {
            config.save();

            // Apply letterbox settings immediately if in borderless fullscreen
            FullscreenManager manager = FullscreenManager.getInstance();
            if (manager.getFullscreenMode() == FullscreenMode.BORDERLESS) {
                ResizableGameRenderer renderer = ResizableGameRenderer.getInstance();
                if (config.isLetterboxEnabled()) {
                    int width = config.getCustomRenderWidth();
                    int height = config.getCustomRenderHeight();
                    if (width > 0 && height > 0) {
                        renderer.resize(width, height, true);
                    }
                } else if (renderer.isLetterboxEnabled()) {
                    // Letterbox was enabled but now disabled - turn it off
                    renderer.disable();
                }
            }
        });

        return builder.build();
    }

    private ClothConfigScreen() { }
}
