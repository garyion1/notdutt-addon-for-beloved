package dev.khanh.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;

/** Analytic antialiased corners in one quad. Shape bounds/radius travel in UVs,
 * so filled shapes and borders batch together with no per-shape uniform buffer.
 */
public final class SmoothGuiShapes {
    private static final Identifier FILL = Identifier.of("khanh", "textures/gui/rounded-fill.png");
    private static final RenderPipeline SHAPE = RenderPipeline.builder()
        .withLocation(Identifier.of("khanh", "pipeline/gui_shape"))
        .withVertexShader(Identifier.of("khanh", "core/gui_shape"))
        .withFragmentShader(Identifier.of("khanh", "core/gui_shape"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withCull(false)
        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).build();
    private SmoothGuiShapes() { }
    public static void rect(DrawContext c, int x, int y, int w, int h, int radius, int color, boolean border) {
        if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            if (!border) c.fill(x, y, x + w, y + h, color);
            else {
                c.fill(x, y, x + w, y + 1, color); c.fill(x, y + h - 1, x + w, y + h, color);
                c.fill(x, y + 1, x + 1, y + h - 1, color); c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            }
            return;
        }
        // Integer magnitudes encode full bounds. The small fractional component
        // encodes radius/outline; both signs decode identically in the vertex shader.
        c.drawTexture(SHAPE, FILL, x, y, -w - r / 1024F, -h - (border ? .25F : .125F) / 1024F,
            w, h, w * 2 + 1, h * 2 + 1, 1, 1, color);
    }
    public static void circle(DrawContext c, int x, int y, int radius, int color) {
        if (radius <= 0) { c.fill(x, y, x + 1, y + 1, color); return; }
        rect(c, x - radius, y - radius, radius * 2, radius * 2, radius, color, false);
    }
}
