package dev.khanh.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;

/** Applies reversible vanilla render settings while FPS Boost is enabled. */
final class PerformanceService {
    private boolean captured;
    private String appliedSignature = "";
    private GraphicsMode graphics;
    private ParticlesMode particles;
    private CloudRenderMode clouds;
    private boolean entityShadows;
    private boolean cutoutLeaves;
    private boolean improvedTransparency;
    private boolean vignette;
    private boolean bobView;
    private double entityDistance;
    private double chunkFade;
    private double fovEffect;
    private double damageTilt;
    private double distortion;
    private double darkness;
    private int biomeBlend;
    private int viewDistance;
    private int simulationDistance;
    private boolean vsync;

    void tick(MinecraftClient client) {
        if (!KhanhFeatures.enabled("fps_boost")) {
            restore(client);
            return;
        }
        if (!captured) {
            capture(client);
        }
        String signature = signature();
        if (!signature.equals(appliedSignature)) {
            apply(client);
            appliedSignature = signature;
        }
    }

    void shutdown(MinecraftClient client) {
        restore(client);
    }

    private void capture(MinecraftClient client) {
        graphics = client.options.getPreset().getValue();
        particles = client.options.getParticles().getValue();
        clouds = client.options.getCloudRenderMode().getValue();
        entityShadows = client.options.getEntityShadows().getValue();
        cutoutLeaves = client.options.getCutoutLeaves().getValue();
        improvedTransparency = client.options.getImprovedTransparency().getValue();
        vignette = client.options.getVignette().getValue();
        bobView = client.options.getBobView().getValue();
        entityDistance = client.options.getEntityDistanceScaling().getValue();
        chunkFade = client.options.getChunkFade().getValue();
        fovEffect = client.options.getFovEffectScale().getValue();
        damageTilt = client.options.getDamageTiltStrength().getValue();
        distortion = client.options.getDistortionEffectScale().getValue();
        darkness = client.options.getDarknessEffectScale().getValue();
        biomeBlend = client.options.getBiomeBlendRadius().getValue();
        viewDistance = client.options.getViewDistance().getValue();
        simulationDistance = client.options.getSimulationDistance().getValue();
        vsync = client.options.getEnableVsync().getValue();
        captured = true;
    }

    private void apply(MinecraftClient client) {
        switch (KhanhFeatures.choice("fps_boost", "graphics", "Fast")) {
            case "Keep" -> client.options.getPreset().setValue(graphics);
            case "Fancy" -> client.options.getPreset().setValue(GraphicsMode.FANCY);
            default -> client.options.getPreset().setValue(GraphicsMode.FAST);
        }
        switch (KhanhFeatures.choice("fps_boost", "particles", "Decreased")) {
            case "Keep" -> client.options.getParticles().setValue(particles);
            case "All" -> client.options.getParticles().setValue(ParticlesMode.ALL);
            case "Minimal" -> client.options.getParticles().setValue(ParticlesMode.MINIMAL);
            default -> client.options.getParticles().setValue(ParticlesMode.DECREASED);
        }
        switch (KhanhFeatures.choice("fps_boost", "clouds", "Off")) {
            case "Keep" -> client.options.getCloudRenderMode().setValue(clouds);
            case "Fast" -> client.options.getCloudRenderMode().setValue(CloudRenderMode.FAST);
            case "Fancy" -> client.options.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
            default -> client.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        }

        client.options.getEntityShadows().setValue(KhanhFeatures.bool("fps_boost", "entity_shadows", false));
        client.options.getCutoutLeaves().setValue(KhanhFeatures.bool("fps_boost", "cutout_leaves", false));
        client.options.getImprovedTransparency().setValue(
            KhanhFeatures.bool("fps_boost", "improved_transparency", false));
        client.options.getVignette().setValue(KhanhFeatures.bool("fps_boost", "vignette", false));
        client.options.getBobView().setValue(KhanhFeatures.bool("fps_boost", "view_bobbing", true));
        client.options.getEntityDistanceScaling().setValue(
            KhanhFeatures.decimal("fps_boost", "entity_distance", 75.0) / 100.0);
        client.options.getChunkFade().setValue(KhanhFeatures.decimal("fps_boost", "chunk_fade", 0.0));
        client.options.getFovEffectScale().setValue(
            KhanhFeatures.decimal("fps_boost", "fov_effect", 80.0) / 100.0);
        client.options.getDamageTiltStrength().setValue(
            KhanhFeatures.decimal("fps_boost", "damage_tilt", 40.0) / 100.0);
        client.options.getDistortionEffectScale().setValue(
            KhanhFeatures.decimal("fps_boost", "distortion", 40.0) / 100.0);
        client.options.getDarknessEffectScale().setValue(
            KhanhFeatures.decimal("fps_boost", "darkness", 70.0) / 100.0);
        client.options.getBiomeBlendRadius().setValue(KhanhFeatures.number("fps_boost", "biome_blend", 0));

        if (KhanhFeatures.bool("fps_boost", "distance_cap", false)) {
            int cap = KhanhFeatures.number("fps_boost", "max_distance", 16);
            client.options.getViewDistance().setValue(Math.min(viewDistance, cap));
            client.options.getSimulationDistance().setValue(Math.min(simulationDistance, cap));
        } else {
            client.options.getViewDistance().setValue(viewDistance);
            client.options.getSimulationDistance().setValue(simulationDistance);
        }
        client.options.getEnableVsync().setValue(
            KhanhFeatures.bool("fps_boost", "disable_vsync", false) ? false : vsync);
    }

    private void restore(MinecraftClient client) {
        if (!captured) {
            return;
        }
        client.options.getPreset().setValue(graphics);
        client.options.getParticles().setValue(particles);
        client.options.getCloudRenderMode().setValue(clouds);
        client.options.getEntityShadows().setValue(entityShadows);
        client.options.getCutoutLeaves().setValue(cutoutLeaves);
        client.options.getImprovedTransparency().setValue(improvedTransparency);
        client.options.getVignette().setValue(vignette);
        client.options.getBobView().setValue(bobView);
        client.options.getEntityDistanceScaling().setValue(entityDistance);
        client.options.getChunkFade().setValue(chunkFade);
        client.options.getFovEffectScale().setValue(fovEffect);
        client.options.getDamageTiltStrength().setValue(damageTilt);
        client.options.getDistortionEffectScale().setValue(distortion);
        client.options.getDarknessEffectScale().setValue(darkness);
        client.options.getBiomeBlendRadius().setValue(biomeBlend);
        client.options.getViewDistance().setValue(viewDistance);
        client.options.getSimulationDistance().setValue(simulationDistance);
        client.options.getEnableVsync().setValue(vsync);
        captured = false;
        appliedSignature = "";
    }

    private String signature() {
        return KhanhFeatures.choice("fps_boost", "graphics", "Fast") + '|'
            + KhanhFeatures.choice("fps_boost", "particles", "Decreased") + '|'
            + KhanhFeatures.choice("fps_boost", "clouds", "Off") + '|'
            + KhanhFeatures.bool("fps_boost", "entity_shadows", false) + '|'
            + KhanhFeatures.bool("fps_boost", "cutout_leaves", false) + '|'
            + KhanhFeatures.bool("fps_boost", "improved_transparency", false) + '|'
            + KhanhFeatures.bool("fps_boost", "vignette", false) + '|'
            + KhanhFeatures.bool("fps_boost", "view_bobbing", true) + '|'
            + KhanhFeatures.decimal("fps_boost", "entity_distance", 75.0) + '|'
            + KhanhFeatures.decimal("fps_boost", "chunk_fade", 0.0) + '|'
            + KhanhFeatures.decimal("fps_boost", "fov_effect", 80.0) + '|'
            + KhanhFeatures.decimal("fps_boost", "damage_tilt", 40.0) + '|'
            + KhanhFeatures.decimal("fps_boost", "distortion", 40.0) + '|'
            + KhanhFeatures.decimal("fps_boost", "darkness", 70.0) + '|'
            + KhanhFeatures.number("fps_boost", "biome_blend", 0) + '|'
            + KhanhFeatures.bool("fps_boost", "distance_cap", false) + '|'
            + KhanhFeatures.number("fps_boost", "max_distance", 16) + '|'
            + KhanhFeatures.bool("fps_boost", "disable_vsync", false);
    }
}
