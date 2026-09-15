package dev.riderpack;

import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterImageButton;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Keep text labels legible where a dependency line passes behind them. */
@EventBusSubscriber(modid = "riderpack", value = Dist.CLIENT)
public final class RiderPackClient {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ClientQuestFile.INSTANCE == null) return;
        ClientQuestFile.INSTANCE.getQuestScreen().ifPresent(screen -> {
            for (Widget widget : screen.questPanel.getWidgets()) {
                if (widget instanceof ChapterImageButton button
                        && button.moveAndDeleteFocus() instanceof ChapterImage image
                        && image.shouldDrawTextOnImage()) {
                    button.setDrawLayer(Widget.DrawLayer.FOREGROUND);
                }
            }
        });
    }
}
