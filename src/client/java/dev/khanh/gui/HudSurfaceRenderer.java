package dev.khanh.gui;

import dev.khanh.KhanhClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import dev.khanh.feature.KhanhFeatures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Continuous liquid-glass surfaces and the Zero HUD icon set. */
public final class HudSurfaceRenderer {
    public static final int TEXT=0xFFF2F5F8, MUTED=0xFFADB7C2, RULE=0x49566570;
    private static final int SURFACE=0xB0090D12, BORDER=0x6B8493A3;
    private HudSurfaceRenderer() { }
    public static void card(DrawContext c,int x,int y,int width,int height) {
        surface(c,x,y,width,height,Math.min(8,height/2),true,true);
    }
    public static void chip(DrawContext c,int x,int y,int width,int height) {
        surface(c,x,y,width,height,Math.min(5,height/2),true,false);
    }
    private static void surface(DrawContext c,int x,int y,int width,int height,int radius,boolean glass,boolean rim) {
        double opacity=Math.max(.4,Math.min(.96,KhanhFeatures.decimal("visuals","glass_opacity",.86)));
        int colour=((int)Math.round(98+opacity*120)<<24)|(SURFACE&0xFFFFFF);
        boolean border=rim&&KhanhFeatures.bool("visuals","hud_border",true);
        // Black frosted glass, the same material as the Zero console.
        if(glass&&LiquidGlass.ready()) {
            LiquidGlass.frost(c,x,y,width,height,radius,(float)Math.max(.6,opacity));
            LiquidGlass.glass(c,x,y,width,height,radius,0xFFFFFF,border?1F:.55F);
            return;
        }
        boolean rendered=glass&&GlassGuiRenderer.hudSurface(c,x,y,width,height,radius,colour,border);
        if(!rendered) {
            SmoothGuiShapes.rect(c,x,y,width,height,radius,colour,false);
            if(border)SmoothGuiShapes.rect(c,x,y,width,height,radius,BORDER,true);
        }
        if(rim&&width>28&&height>16&&KhanhFeatures.bool("visuals","hud_sheen",true)) {
            SmoothGuiShapes.rect(c,x+radius+5,y+1,width-2*radius-10,1,0,0x22FFFFFF,false);
        }
    }
    public static void itemIcon(DrawContext c,ItemStack item,int x,int y,int size) {
        c.getMatrices().pushMatrix();
        try {
            c.getMatrices().translate(x,y);c.getMatrices().scale(size/16F,size/16F);
            c.drawItemWithoutEntity(item,0,0);
        } finally { c.getMatrices().popMatrix(); }
    }
    public static void icon(DrawContext c,HudIcon icon,int x,int y,int size,int tint) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED,icon.texture,x,y,0F,0F,size,size,128,128,128,128,tint);
    }
    public static void dial(DrawContext c,int x,int y,int size) {
        int radius=size/2-3,cx=x+size/2,cy=y+size/2;
        surface(c,cx-radius,cy-radius,radius*2,radius*2,radius,true,true);
        int guide=dev.khanh.feature.VisualTheme.withAlpha(dev.khanh.feature.VisualTheme.hudAccent(),42);
        c.fill(cx-radius+10,cy,cx+radius-10,cy+1,guide);
        c.fill(cx,cy-radius+10,cx+1,cy+radius-10,guide);
        SmoothGuiShapes.circle(c,cx,cy,2,TEXT);
    }
    public static void radar(DrawContext c,int x,int y,int size) {
        int radius=size/2-3,cx=x+size/2,cy=y+size/2;
        surface(c,cx-radius,cy-radius,radius*2,radius*2,radius,true,true);
        int guide=dev.khanh.feature.VisualTheme.withAlpha(dev.khanh.feature.VisualTheme.hudAccent(),42);
        c.fill(cx-radius+10,cy,cx+radius-10,cy+1,guide);
        c.fill(cx,cy-radius+10,cx+1,cy+radius-10,guide);
    }

    public enum HudIcon {
        INVENTORY("inventory"), PLAYER("player"), WAND("wand"), COMPASS("compass"),
        WORLD("world"), FOCUS("focus"), KEYBINDS("keybinds"), MODULES("modules"),
        SPEED("speed"), POTIONS("potions"), DOCUMENT("document"), SEARCH("search"),
        SPOTIFY("spotify"), COMBAT("combat"), RADAR("radar"), CLOCK("clock");

        private final Identifier texture;
        HudIcon(String file) { this.texture=Identifier.of(KhanhClient.MOD_ID,"textures/hud/"+file+".png"); }
    }
}
