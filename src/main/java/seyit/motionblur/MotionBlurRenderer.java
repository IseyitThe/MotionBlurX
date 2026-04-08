package seyit.motionblur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.Pool;
import net.minecraft.util.Identifier;
import seyit.motionblur.config.MotionBlurConfig;
import seyit.motionblur.mixin.GameRendererAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class MotionBlurRenderer {

    private static final Identifier EFFECT_ID = Identifier.of(MotionBlurMod.ID, "motion_blur");
    private static final Identifier PREVIOUS_TARGET_ID = Identifier.of(MotionBlurMod.ID, "previous");
    private static final String BLEND_FACTOR_UNIFORM = "BlendFactor";

    private static PostEffectProcessor processor;
    private static SimpleFramebuffer previousFramebuffer;
    private static boolean historyPrimed;
    private static boolean loadFailureLogged;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private MotionBlurRenderer() {
    }

    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            resetHistory();
            return;
        }

        int amount = MotionBlurConfig.getMotionBlurAmount();
        if (amount <= 0) {
            resetHistory();
            return;
        }

        PostEffectProcessor effect = getProcessor(client);
        if (effect == null) {
            return;
        }

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer.textureWidth != lastWidth || framebuffer.textureHeight != lastHeight) {
            lastWidth = framebuffer.textureWidth;
            lastHeight = framebuffer.textureHeight;
            clearTemporalState();
        }

        ensurePreviousFramebuffer(framebuffer);

        float blendFactor = historyPrimed ? Math.min(amount, 99) / 100.0F : 0.0F;

        Pool pool = ((GameRendererAccessor) client.gameRenderer).getPool();
        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        PostEffectProcessor.FramebufferSet framebufferSet = createFramebufferSet(frameGraphBuilder, framebuffer);
        int[] passIndex = {0};
        effect.render(frameGraphBuilder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet, renderPass -> {
            if (passIndex[0]++ == 0) {
                renderPass.setUniform(BLEND_FACTOR_UNIFORM, blendFactor);
            }
        });
        frameGraphBuilder.run(pool);
        historyPrimed = true;
    }

    public static void resetHistory() {
        lastWidth = -1;
        lastHeight = -1;
        clearTemporalState();
    }

    public static void invalidate() {
        processor = null;
        loadFailureLogged = false;
        deletePreviousFramebuffer();
        resetHistory();
    }

    private static PostEffectProcessor getProcessor(MinecraftClient client) {
        if (processor != null) {
            return processor;
        }

        try {
            processor = client.getShaderLoader().loadPostEffect(EFFECT_ID, Set.of(PostEffectProcessor.MAIN, PREVIOUS_TARGET_ID));
            if (processor == null) {
                if (!loadFailureLogged) {
                    MotionBlurMod.LOGGER.error("Failed to load motion blur post effect: shader loader returned null");
                    loadFailureLogged = true;
                }
                return null;
            }
            loadFailureLogged = false;
            return processor;
        } catch (RuntimeException e) {
            if (!loadFailureLogged) {
                MotionBlurMod.LOGGER.error("Failed to load motion blur post effect", e);
                loadFailureLogged = true;
            }
            return null;
        }
    }

    private static void ensurePreviousFramebuffer(Framebuffer framebuffer) {
        if (previousFramebuffer != null
                && previousFramebuffer.textureWidth == framebuffer.textureWidth
                && previousFramebuffer.textureHeight == framebuffer.textureHeight) {
            return;
        }

        deletePreviousFramebuffer();
        previousFramebuffer = new SimpleFramebuffer("motionblur previous", framebuffer.textureWidth, framebuffer.textureHeight, false);
        clearTemporalState();
    }

    private static PostEffectProcessor.FramebufferSet createFramebufferSet(FrameGraphBuilder frameGraphBuilder, Framebuffer framebuffer) {
        Map<Identifier, Handle<Framebuffer>> handles = new HashMap<>();
        handles.put(PostEffectProcessor.MAIN, frameGraphBuilder.createObjectNode("main", framebuffer));
        handles.put(PREVIOUS_TARGET_ID, frameGraphBuilder.createObjectNode("motionblur_previous", previousFramebuffer));
        return new PostEffectProcessor.FramebufferSet() {
            @Override
            public void set(Identifier id, Handle<Framebuffer> handle) {
                handles.put(id, handle);
            }

            @Override
            public Handle<Framebuffer> get(Identifier id) {
                return handles.get(id);
            }
        };
    }

    private static void deletePreviousFramebuffer() {
        if (previousFramebuffer != null) {
            previousFramebuffer.delete();
            previousFramebuffer = null;
        }
    }

    private static void clearTemporalState() {
        historyPrimed = false;
    }
}
