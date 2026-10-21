package forge.util;

/**
 * Compile-time switch for GXIzikiel's custom iOS UI adaptations.
 * false = upstream Forge UI; true = custom iPhone safe area, UI scaling,
 * and classic corner-button visual extensions.
 *
 * The iOS build, release and AltStore publishing workflow is unaffected.
 */
public final class IosForkBuildFlags {
    private IosForkBuildFlags() { }

    public static final boolean ENABLE_CUSTOM_IOS_UI = false;
}
