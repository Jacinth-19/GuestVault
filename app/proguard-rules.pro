# ---------------------------------------------------------------------------
# GuestVault release rules (R8 + resource shrinking)
# ---------------------------------------------------------------------------

-keepattributes SourceFile,LineNumberTable

# App + generated Hilt code.
-keep class com.guestvault.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Tink (used by androidx.security:security-crypto). It instantiates key managers
# reflectively; without these rules EncryptedSharedPreferences fails at runtime
# and GuestVault would silently fall back to its hash-only preferences file.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.KeyManager { *; }
-dontwarn com.google.crypto.tink.**

# Annotation-only dependencies that are not on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.conscrypt.**
-dontwarn org.joda.time.**

# Compose keeps its own consumer rules; nothing extra is required here.
