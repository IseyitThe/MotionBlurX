package seyit.motionblur;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class MotionBlurResourceReloader implements IdentifiableResourceReloadListener, SynchronousResourceReloader {

    private static final Identifier ID = Identifier.of(MotionBlurMod.ID, "reload");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        MotionBlurRenderer.invalidate();
    }
}
