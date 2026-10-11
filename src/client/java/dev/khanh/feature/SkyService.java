package dev.khanh.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** Direct sky rendering: no optional resource packs or reloads when switching skies. */
public final class SkyService {
    private static final int LONGITUDES = 64;
    private static final int LATITUDES = 32;
    private static final int INDEX_COUNT = LONGITUDES * LATITUDES * 6;
    private static final long START = System.nanoTime();
    private static GpuBuffer vertices;
    private static GpuBuffer skyParameters;
    private static final RenderPipeline PROCEDURAL = RenderPipeline.builder()
        .withLocation(Identifier.of("khanh", "pipeline/procedural_sky"))
        .withVertexShader(Identifier.of("khanh", "core/procedural_sky"))
        .withFragmentShader(Identifier.of("khanh", "core/procedural_sky"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("SkyEffects", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
        .withCull(false).withoutBlend()
        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).build();

    private SkyService() {}
    public static void register() {}
    private static final Identifier HD_SKY = Identifier.of("khanh", "dynamic/sky_hd");
    private static volatile String loadedSky = "";
    private static volatile String loadingSky = "";

    /** Starts decoding the chosen picture sky in the background when it changes. */
    public static void tick(MinecraftClient client) {
        if (!KhanhFeatures.enabled("shader_fog")) return;
        String choice = KhanhFeatures.choice("shader_fog", "sky", "Water");
        if (PATTERNS.contains(choice) || choice.equals(loadedSky) || choice.equals(loadingSky)) return;
        loadingSky = choice;
        Thread.ofVirtual().name("khanh-sky-load").start(() -> loadHd(client, choice));
    }

    /**
     * Picture skies are full-resolution 4096x2048 JPEGs (sky_hd/), sharp at any field of view.
     * Minecraft only reads PNG textures, so they are decoded here and uploaded directly.
     */
    private static void loadHd(MinecraftClient client, String choice) {
        String file = "sky_hd/" + choice.toLowerCase(java.util.Locale.ROOT).replace(' ', '_') + ".jpg";
        try {
            var resource = client.getResourceManager().getResource(Identifier.of("khanh", file));
            if (resource.isEmpty()) throw new java.io.FileNotFoundException(file);
            java.awt.image.BufferedImage picture;
            try (var in = resource.get().getInputStream()) { picture = javax.imageio.ImageIO.read(in); }
            if (picture == null) throw new java.io.IOException("unreadable " + file);
            int w = picture.getWidth(), h = picture.getHeight();
            int[] argb = picture.getRGB(0, 0, w, h, null, 0, w);
            NativeImage image = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setColorArgb(x, y, argb[y * w + x] | 0xFF000000);
            client.execute(() -> {
                if (!choice.equals(loadingSky)) { image.close(); return; }
                client.getTextureManager().registerTexture(HD_SKY, new NativeImageBackedTexture(() -> "Khanh Client sky " + choice, image));
                loadedSky = choice;
                loadingSky = "";
            });
        } catch (Exception exception) {
            dev.khanh.KhanhClient.LOGGER.warn("Custom Sky could not load {}", file, exception);
            client.execute(() -> { if (choice.equals(loadingSky)) loadingSky = ""; loadedSky = ""; });
        }
    }

    /**
     * Custom Sky's choices, listed in assets/khanh/sky_styles.txt: the animated patterns
     * (Water, Caustic, Aurora) and skies converted from the user's resource packs.
     */
    public static final String[] SKIES = loadSkies();
    private static final java.util.Set<String> PATTERNS = java.util.Set.of("Water", "Caustic", "Aurora");

    private static String[] loadSkies() {
        try (var in = SkyService.class.getResourceAsStream("/assets/khanh/sky_styles.txt")) {
            if (in != null) {
                String[] skies = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty()).toArray(String[]::new);
                if (skies.length > 0) return skies;
            }
        } catch (java.io.IOException ignored) {
        }
        return new String[] {"Water", "Caustic", "Aurora"};
    }

    public static boolean active() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !client.world.getRegistryKey().equals(World.OVERWORLD) || !KhanhFeatures.enabled("shader_fog")) return false;
        // A picture sky shows once it has loaded; until then the normal sky stays.
        String choice = KhanhFeatures.choice("shader_fog", "sky", "Water");
        return PATTERNS.contains(choice) || choice.equals(loadedSky);
    }

    public static void render() {
        if (vertices == null) vertices = createMesh();
        MinecraftClient client = MinecraftClient.getInstance();
        String choice = KhanhFeatures.choice("shader_fog", "sky", "Water");
        if (PATTERNS.contains(choice)) {
            renderProcedural(client, choice, true);
            return;
        }
        var texture = client.getTextureManager().getTexture(HD_SKY);
        double seconds = (System.nanoTime() - START) / 1_000_000_000.0;
        double degrees = seconds * KhanhFeatures.decimal("shader_fog", "rotation_speed", 0.6);
        Matrix4f model = new Matrix4f(RenderSystem.getModelViewMatrix())
            .rotateY((float) Math.toRadians(degrees % 360.0)).rotateZ(0.22F);
        var uniforms = RenderSystem.getDynamicUniforms().write(model, new Vector4f(1, 1, 1, 1),
            new Vector3f(), new Matrix4f());
        var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        var indexBuffer = indices.getIndexBuffer(INDEX_COUNT);
        var framebuffer = client.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Khanh Client galaxy sky", framebuffer.getColorAttachmentView(), OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_END_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", uniforms);
            // Smooth filtering, wrapping round so the panorama has no seam where it meets itself.
            pass.bindTexture("Sampler0", texture.getGlTextureView(),
                RenderSystem.getSamplerCache().getRepeated(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            pass.drawIndexed(0, 0, INDEX_COUNT, 1);
        }
    }

    private static void renderProcedural(MinecraftClient client, String mode, boolean customControls) {
        if (skyParameters == null) skyParameters = RenderSystem.getDevice().createBuffer(
            () -> "Khanh Client procedural sky parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        var data = MemoryUtil.memAlloc(32);
        try {
            int tint = customControls ? KhanhFeatures.color("shader_fog", "color", 0xFF8078D8)
                : mode.equals("Aurora") ? 0xFF5CE8BC : mode.equals("Caustic") ? 0xFF9FD6FA : 0xFF6B95E8;
            data.putFloat((tint >> 16 & 255) / 255F).putFloat((tint >> 8 & 255) / 255F)
                .putFloat((tint & 255) / 255F).putFloat((System.nanoTime() - START) / 1_000_000_000F);
            data.putFloat(customControls ? (float) KhanhFeatures.decimal("shader_fog", "speed", 1)
                    : .8F)
                .putFloat(customControls ? (float) KhanhFeatures.decimal("shader_fog", "scale", 5) : 5F)
                .putFloat(customControls ? (float) KhanhFeatures.decimal("shader_fog", "intensity", .7) : .7F)
                .putFloat(mode.equals("Aurora") ? 2 : mode.equals("Caustic") ? 1 : 0);
            data.flip(); RenderSystem.getDevice().createCommandEncoder().writeToBuffer(skyParameters.slice(), data);
        } finally { MemoryUtil.memFree(data); }
        var transforms = RenderSystem.getDynamicUniforms().write(new Matrix4f(RenderSystem.getModelViewMatrix()),
            new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        var indexBuffer = indices.getIndexBuffer(INDEX_COUNT);
        var framebuffer = client.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Khanh Client animated water sky", framebuffer.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(PROCEDURAL); RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms); pass.setUniform("SkyEffects", skyParameters);
            pass.setVertexBuffer(0, vertices); pass.setIndexBuffer(indexBuffer, indices.getIndexType());
            pass.drawIndexed(0, 0, INDEX_COUNT, 1);
        }
    }


    private static GpuBuffer createMesh() {
        try (BufferAllocator allocator = BufferAllocator.fixedSized(LONGITUDES * LATITUDES * 4
                * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR);
            for (int y = 0; y < LATITUDES; y++) {
                for (int x = 0; x < LONGITUDES; x++) {
                    // The camera sits inside this sphere. Wind quads inward so
                    // the sky pipeline's back-face culling does not discard it.
                    vertex(builder, x, y);
                    vertex(builder, x + 1, y);
                    vertex(builder, x + 1, y + 1);
                    vertex(builder, x, y + 1);
                }
            }
            try (BuiltBuffer built = builder.end()) {
                return RenderSystem.getDevice().createBuffer(() -> "Khanh Client sky mesh", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }

    private static void vertex(BufferBuilder builder, int x, int y) {
        float u = (float) x / LONGITUDES;
        float v = (float) y / LATITUDES;
        double longitude = u * Math.PI * 2;
        double latitude = (v - 0.5) * Math.PI;
        double ring = Math.cos(latitude) * 100;
        builder.vertex((float) (Math.cos(longitude) * ring), (float) (Math.sin(latitude) * 100),
            (float) (Math.sin(longitude) * ring)).texture(u, v).color(0xFFFFFFFF);
    }

    public static void close() {
        if (skyParameters != null) { skyParameters.close(); skyParameters = null; }
        if (vertices != null) {
            vertices.close();
            vertices = null;
        }
    }
}
