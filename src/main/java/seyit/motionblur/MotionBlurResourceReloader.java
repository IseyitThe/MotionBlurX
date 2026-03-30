package seyit.motionblur;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MotionBlurResourceReloader implements IdentifiableResourceReloadListener, SimpleSynchronousResourceReloadListener {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(MotionBlurMod.ID, "reload");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MotionBlurRenderer.invalidate();
    }
}
