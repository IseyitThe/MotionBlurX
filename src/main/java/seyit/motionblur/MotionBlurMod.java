package seyit.motionblur;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.text.Text;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seyit.motionblur.config.MotionBlurConfig;

public class MotionBlurMod implements ClientModInitializer {

    public static final String ID = "motionblur";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitializeClient() {
        MotionBlurConfig.load();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new MotionBlurResourceReloader());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("motionblur")
                        .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> changeAmount(context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))
        ));

        HudRenderCallback.EVENT.register((context, tickCounter) -> MotionBlurRenderer.render());
    }

    private static int changeAmount(FabricClientCommandSource src, int amount) {
        MotionBlurConfig.setMotionBlurAmount(amount);
        MotionBlurRenderer.resetHistory();
        src.sendFeedback(Text.literal("Motion Blur: " + amount + "%"));
        return amount;
    }
}
