package dev.khanh.feature;

/**
 * Mining in Freecam works like Free Look: your player keeps aiming where it was looking when you
 * entered Freecam, and holding attack mines that, exactly as Minecraft normally would. Moving or
 * turning the camera never changes what is being mined.
 */
public final class FreecamMiningService {
    private FreecamMiningService() { }

    /** On by default (Freecam's Mining option); the Freecam Mining module also turns it on. */
    public static boolean enabled() {
        return KhanhFeatures.enabled("freecam_mining") || KhanhFeatures.bool("freecam", "mining", true);
    }
}
