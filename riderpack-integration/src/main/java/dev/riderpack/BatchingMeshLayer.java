package dev.riderpack;

import net.minecraft.client.renderer.RenderType;

/** Geometry identity of an ImmediatelyFast draw-state wrapper, for AR's mesh cache only. */
public interface BatchingMeshLayer {
    RenderType riderpack$meshLayer();

    static RenderType unwrap(RenderType layer) {
        while (layer instanceof BatchingMeshLayer wrapped) {
            layer = wrapped.riderpack$meshLayer();
        }
        return layer;
    }
}
