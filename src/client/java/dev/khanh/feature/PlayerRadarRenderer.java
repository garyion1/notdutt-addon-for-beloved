package dev.khanh.feature;

import dev.khanh.gui.HudSurfaceRenderer;
import dev.khanh.gui.KhanhClickGuiScreen;
import dev.khanh.gui.SmoothGuiShapes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/** Local loaded-player radar. Cached membership, interpolated positions, no scans or sweep animation. */
public final class PlayerRadarRenderer {
    private record Nearby(AbstractClientPlayerEntity player, double squared) { }
    private record Contact(AbstractClientPlayerEntity player, RadarLayout.Point point,
                           double distance, double height, boolean friend, boolean selected) { }
    private static ClientWorld cachedWorld;
    private static List<Nearby> nearby = List.of();
    private static long nextRefresh;
    private static int cachedRange, cachedLimit, totalInRange;
    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cachedWorld != null && (client.world != cachedWorld || !KhanhFeatures.enabled("player_radar"))) {
                cachedWorld = null; nearby = List.of(); nextRefresh = 0; totalInRange = 0;
            }
        });
    }
    private PlayerRadarRenderer() { }

    public static int size() { return Math.max(76, Math.min(240, KhanhFeatures.number("player_radar", "size", 148))); }

    private static void refresh(MinecraftClient client, int range, int limit) {
        long now = System.nanoTime();
        if (cachedWorld == client.world && cachedRange == range && cachedLimit == limit && now < nextRefresh) return;
        cachedWorld = client.world; cachedRange = range; cachedLimit = limit; nextRefresh = now + 100_000_000L;
        totalInRange = 0;
        // Keep only the nearest K candidates: bounded memory and O(N log K), not a full sort every frame.
        var closest = new PriorityQueue<Nearby>(Comparator.comparingDouble(Nearby::squared).reversed());
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive() || player.isRemoved() || player.isSpectator()) continue;
            double dx = player.getX() - client.player.getX(), dz = player.getZ() - client.player.getZ();
            double squared = dx * dx + dz * dz;
            if (squared > (double) range * range) continue;
            totalInRange++;
            if (closest.size() < limit) closest.add(new Nearby(player, squared));
            else if (squared < closest.peek().squared) { closest.poll(); closest.add(new Nearby(player, squared)); }
        }
        var sorted = new ArrayList<>(closest); sorted.sort(Comparator.comparingDouble(Nearby::squared));
        nearby = List.copyOf(sorted);
    }

    public static int content(DrawContext c, MinecraftClient client, int x, int y, int size) {
        int cx = x + size / 2, cy = y + size / 2, radius = size / 2 - 3;
        int range = Math.max(24, Math.min(256, KhanhFeatures.number("player_radar", "range", 96)));
        int head = Math.max(8, Math.min(20, KhanhFeatures.number("player_radar", "head_size", 12)));
        int limit = Math.max(8, Math.min(48, KhanhFeatures.number("player_radar", "max_players", 24)));
        boolean rotate = KhanhFeatures.bool("player_radar", "rotate", true);
        float tick = client.getRenderTickCounter().getTickProgress(false);
        double yaw = client.gameRenderer.getCamera().getYaw();
        Vec3d base = client.player.getLerpedPos(tick);
        int accent = VisualTheme.hudAccent();
        if (KhanhFeatures.bool("player_radar", "distance_rings", true)) {
            for (int ring = 1; ring <= 2; ring++) {
                int r = (radius - 6) * ring / 3;
                SmoothGuiShapes.rect(c, cx - r, cy - r, r * 2, r * 2, r, 0x245F7B99, true);
            }
        }
        var occupied = new ArrayList<RadarLayout.Rect>();
        if (KhanhFeatures.bool("player_radar", "cardinals", true)) {
            String[] names = {"N", "E", "S", "W"};
            double[][] directions = {{0,-1},{1,0},{0,1},{-1,0}};
            for (int i = 0; i < names.length; i++) {
                var p = RadarLayout.project(directions[i][0], directions[i][1], yaw, rotate, radius - 10);
                Text text = text(names[i]); int w = client.textRenderer.getWidth(text);
                int lx = (int) Math.round(p.x()) - w / 2, ly = (int) Math.round(p.y()) - 4;
                c.drawText(client.textRenderer, text, cx + lx, cy + ly, i == 0 ? accent : HudSurfaceRenderer.MUTED, false);
                occupied.add(new RadarLayout.Rect(lx - 2, ly - 1, w + 4, 11));
            }
        }
        refresh(client, range, limit);
        var contacts = new ArrayList<Contact>(nearby.size());
        double scale = Math.max(8, radius - head * .71 - 12) / range;
        for (Nearby candidate : nearby) {
            var player = candidate.player;
            if (!player.isAlive() || player.isRemoved()) continue;
            Vec3d delta = player.getLerpedPos(tick).subtract(base);
            double distance = Math.hypot(delta.x, delta.z);
            if (distance > range) continue;
            var point = RadarLayout.project(delta.x, delta.z, yaw, rotate, scale);
            boolean friend = KhanhFeatures.enabled("friends") && FriendService.isFriend(player.getGameProfile().name());
            boolean selected = client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() == player;
            contacts.add(new Contact(player, point, distance, delta.y, friend, selected));
            occupied.add(new RadarLayout.Rect((int)Math.round(point.x()) - head/2 - 3,
                (int)Math.round(point.y()) - head/2 - 3, head+12, head+6));
        }
        int selfHead = Math.min(22, head + 2);
        int selfClear = selfHead/2+12;
        occupied.add(new RadarLayout.Rect(-selfClear,-selfClear,selfClear*2,selfClear*2));
        contacts.sort(Comparator.comparingDouble(Contact::distance));
        // Farther portraits first, so nearby players remain visible in crowded areas.
        for (int i = contacts.size() - 1; i >= 0; i--) {
            Contact contact = contacts.get(i);
            int px = cx + (int)Math.round(contact.point.x()), py = cy + (int)Math.round(contact.point.y());
            int colour = contact.friend ? FriendService.color() : contact.selected ? 0xFFFFC778 : accent;
            portrait(c, contact.player, px, py, head, colour);
            if (KhanhFeatures.bool("player_radar", "height", true) && Math.abs(contact.height) >= 4) {
                int direction = contact.height > 0 ? -1 : 1, ax = px + head/2 + 4;
                segment(c,ax-2,py-direction*2,ax,py+direction*2,colour);
                segment(c,ax,py+direction*2,ax+2,py-direction*2,colour);
            }
        }
        int labels = Math.max(0, Math.min(8, KhanhFeatures.number("player_radar", "label_limit", 4)));
        if (KhanhFeatures.bool("player_radar", "names", true) && labels > 0) {
            var priority = new ArrayList<>(contacts);
            priority.sort(Comparator.comparingInt((Contact a) -> a.selected ? 0 : a.friend ? 1 : 2).thenComparingDouble(Contact::distance));
            int drawn = 0;
            for (Contact contact : priority) {
                if (drawn >= labels) break;
                String suffix = KhanhFeatures.bool("player_radar", "distances", true) ? " " + Math.round(contact.distance) + "m" : "";
                String name = fit(client, PrivacyService.protectedName(contact.player.getGameProfile().name()), Math.max(16,size/2 - 16 - client.textRenderer.getWidth(text(suffix))));
                Text label = text(name + suffix); int width = client.textRenderer.getWidth(label) + 6;
                RadarLayout.Rect rect = RadarLayout.label(contact.point.x(),contact.point.y(),head,width,12,radius-7,occupied);
                if (rect == null) continue;
                occupied.add(rect); drawn++;
                HudSurfaceRenderer.chip(c,cx+rect.x(),cy+rect.y(),rect.width(),rect.height());
                c.drawText(client.textRenderer,label,cx+rect.x()+3,cy+rect.y()+2,contact.friend?FriendService.color():HudSurfaceRenderer.TEXT,false);
            }
        }
        // Facing indicator follows your real yaw, even when Freelook changes the radar view.
        double facing = Math.toRadians(client.player.getLerpedYaw(tick));
        var forward = RadarLayout.project(-Math.sin(facing), Math.cos(facing), yaw, rotate, 1);
        double tip = selfHead/2.0+10, back = selfHead/2.0+5;
        segment(c,cx+forward.x()*back-forward.y()*3,cy+forward.y()*back+forward.x()*3,cx+forward.x()*tip,cy+forward.y()*tip,accent);
        segment(c,cx+forward.x()*tip,cy+forward.y()*tip,cx+forward.x()*back+forward.y()*3,cy+forward.y()*back-forward.x()*3,accent);
        portrait(c, client.player, cx, cy, selfHead, 0xFFC4DDF4);
        // Just the dial: no "nearby" count or list under it.
        return size + 5;
    }

    private static Text text(String value) { return KhanhClickGuiScreen.fontText(value); }
    private static String fit(MinecraftClient client, String value, int width) {
        if(width<=0)return "";
        if(client.textRenderer.getWidth(text(value))<=width)return value;
        String result = value;
        while (!result.isEmpty() && client.textRenderer.getWidth(text(result+"…"))>width) result=result.substring(0,result.length()-1);
        if(result.isEmpty())return client.textRenderer.getWidth(text("…"))<=width?"…":"";
        return result+"…";
    }
    private static void portrait(DrawContext c, AbstractClientPlayerEntity player, int x, int y, int head, int colour) {
        int half = head/2;
        SmoothGuiShapes.rect(c,x-half-2,y-half-2,head+4,head+4,4,0xD0101925,false);
        SmoothGuiShapes.rect(c,x-half-2,y-half-2,head+4,head+4,4,VisualTheme.withAlpha(colour,190),true);
        PlayerSkinDrawer.draw(c,player.getSkin(),x-half,y-half,head);
    }
    private static void segment(DrawContext c, double x0,double y0,double x1,double y1,int colour) {
        c.getMatrices().pushMatrix();
        try {
            c.getMatrices().translate((float)x0,(float)y0);
            c.getMatrices().rotate((float)Math.atan2(y1-y0,x1-x0));
            SmoothGuiShapes.rect(c,0,-1,Math.max(1,(int)Math.round(Math.hypot(x1-x0,y1-y0))),2,1,colour,false);
        } finally { c.getMatrices().popMatrix(); }
    }
}
