package seyit.motionblur;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seyit.motionblur.config.MotionBlurConfig;

public class MotionBlurMod implements ClientModInitializer {

    public static final String ID = "motionblur";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitializeClient() {
        MotionBlurConfig.load();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new MotionBlurResourceReloader());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("motionblur")
                        .then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> changeAmount(context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))
        ));
    }

    private static int changeAmount(FabricClientCommandSource src, int amount) {
        MotionBlurConfig.setMotionBlurAmount(amount);
        MotionBlurRenderer.resetHistory();
        src.sendFeedback(Component.literal("Motion Blur: " + amount + "%"));
        return amount;
    }
}
