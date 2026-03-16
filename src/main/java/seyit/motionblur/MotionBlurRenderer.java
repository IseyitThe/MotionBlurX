package seyit.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.Pool;
import net.minecraft.util.Identifier;
import seyit.motionblur.config.MotionBlurConfig;
import seyit.motionblur.mixin.GameRendererAccessor;
import seyit.motionblur.mixin.PostEffectPassAccessor;
import seyit.motionblur.mixin.PostEffectProcessorAccessor;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MotionBlurRenderer {

    private static final Identifier EFFECT_ID = Identifier.of(MotionBlurMod.ID, "motion_blur");
    private static final String UNIFORM_GROUP = "MotionBlurConfig";

    private static PostEffectProcessor processor;
    private static GpuBuffer blendBuffer;
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
        if (effect == null || blendBuffer == null) {
            return;
        }

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer.textureWidth != lastWidth || framebuffer.textureHeight != lastHeight) {
            lastWidth = framebuffer.textureWidth;
            lastHeight = framebuffer.textureHeight;
            clearTemporalState();
        }

        float blendFactor = historyPrimed ? Math.min(amount, 99) / 100.0F : 0.0F;
        writeBlendFactor(blendFactor);

        Pool pool = ((GameRendererAccessor) client.gameRenderer).getPool();
        effect.render(framebuffer, pool);
        historyPrimed = true;
    }

    public static void resetHistory() {
        lastWidth = -1;
        lastHeight = -1;
        clearTemporalState();
    }

    public static void invalidate() {
        processor = null;
        blendBuffer = null;
        loadFailureLogged = false;
        resetHistory();
    }

    private static PostEffectProcessor getProcessor(MinecraftClient client) {
        if (blendBuffer != null && blendBuffer.isClosed()) {
            invalidate();
        }

        if (processor != null && blendBuffer != null) {
            return processor;
        }

        try {
            processor = Objects.requireNonNull(client.getShaderLoader().loadPostEffect(EFFECT_ID, Set.of(DefaultFramebufferSet.MAIN)));
            blendBuffer = RenderSystem.getDevice().createBuffer(() -> "motionblur blend", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 4);
            patchUniforms(processor, blendBuffer);
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

    private static void patchUniforms(PostEffectProcessor effect, GpuBuffer buffer) {
        for (PostEffectPass pass : ((PostEffectProcessorAccessor) effect).getPasses()) {
            Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) pass).getUniformBuffers();
            if (!uniformBuffers.containsKey(UNIFORM_GROUP)) {
                continue;
            }

            GpuBuffer oldBuffer = uniformBuffers.put(UNIFORM_GROUP, buffer);
            if (oldBuffer != null) {
                oldBuffer.close();
            }
        }
    }

    private static void writeBlendFactor(float blendFactor) {
        try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(blendBuffer, false, true)) {
            Std140Builder.intoBuffer(mappedView.data()).putFloat(blendFactor);
        }
    }

    private static void clearTemporalState() {
        historyPrimed = false;
    }
}
