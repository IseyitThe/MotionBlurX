package seyit.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import seyit.motionblur.config.MotionBlurConfig;
import seyit.motionblur.mixin.GameRendererAccessor;
import seyit.motionblur.mixin.PostEffectPassAccessor;
import seyit.motionblur.mixin.PostEffectProcessorAccessor;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MotionBlurRenderer {

    private static final Identifier EFFECT_ID = Identifier.fromNamespaceAndPath(MotionBlurMod.ID, "motion_blur");
    private static final Identifier MAIN_TARGET_ID = Identifier.fromNamespaceAndPath("minecraft", "main");
    private static final String UNIFORM_GROUP = "MotionBlurConfig";

    private static PostChain processor;
    private static GpuBuffer blendBuffer;
    private static boolean historyPrimed;
    private static boolean loadFailureLogged;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private MotionBlurRenderer() {
    }

    public static void render() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            resetHistory();
            return;
        }

        int amount = MotionBlurConfig.getMotionBlurAmount();
        if (amount <= 0) {
            resetHistory();
            return;
        }

        PostChain effect = getProcessor(client);
        if (effect == null || blendBuffer == null) {
            return;
        }

        RenderTarget framebuffer = client.getMainRenderTarget();
        if (framebuffer.width != lastWidth || framebuffer.height != lastHeight) {
            lastWidth = framebuffer.width;
            lastHeight = framebuffer.height;
            clearTemporalState();
        }

        float blendFactor = historyPrimed ? Math.min(amount, 99) / 100.0F : 0.0F;
        writeBlendFactor(blendFactor);

        CrossFrameResourcePool resourcePool = ((GameRendererAccessor) client.gameRenderer).getResourcePool();
        effect.process(framebuffer, resourcePool);
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

    private static PostChain getProcessor(Minecraft client) {
        if (blendBuffer != null && blendBuffer.isClosed()) {
            invalidate();
        }

        if (processor != null && blendBuffer != null) {
            return processor;
        }

        try {
            processor = Objects.requireNonNull(client.getShaderManager().getPostChain(EFFECT_ID, Set.of(MAIN_TARGET_ID)));
            blendBuffer = RenderSystem.getDevice().createBuffer(() -> "motionblur blend", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 16);
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

    private static void patchUniforms(PostChain effect, GpuBuffer buffer) {
        for (PostPass pass : ((PostEffectProcessorAccessor) effect).getPasses()) {
            Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) pass).getCustomUniforms();
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
