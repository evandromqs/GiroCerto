# ML Kit and AndroidX ship consumer rules. App JSON persistence uses explicit field mapping.
# Firebase discovers these classes by manifest name and calls getDeclaredConstructor().
# The dependency's class-only keep rule does not retain constructors with this R8 build.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
