# =============================================
# MarkdownReader ProGuard Rules
# =============================================

# ==================== Android Framework ====================

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions,InnerClasses

-renamesourcefileattribute SourceFile
-keepattributes SourceFile

# ==================== Kotlin ====================

-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ==================== Jetpack Compose ====================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# ==================== Room Database ====================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Room generated classes
-keep class * extends androidx.room.RoomDatabase { *; }

# ==================== Hilt DI ====================

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Hilt ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Hilt components
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# ==================== Markwon (Markdown) ====================

-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# Markwon extensions
-keep class io.noties.markwon.ext.** { *; }
-keep class io.noties.markwon.html.** { *; }
-keep class io.noties.markwon.image.** { *; }
-keep class io.noties.markwon.inlineparser.** { *; }

# JLatexMath (LaTeX rendering)
-keep class io.noties.markwon.ext.latex.** { *; }
-dontwarn io.noties.markwon.ext.latex.**

# ==================== Data Classes / Entities ====================

-keep class space.liushenme.markdownreader.data.local.entity.** { *; }
-keep class space.liushenme.markdownreader.model.** { *; }

# ==================== Gson ====================

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Application data classes
-keep class space.liushenme.markdownreader.importing.** { *; }
-keep class space.liushenme.markdownreader.data.** { *; }

# ==================== Vico Charts ====================

-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# ==================== AndroidSVG ====================

-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# ==================== Navigation ====================

-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }

# Navigation arguments
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# ==================== DataStore ====================

-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# ==================== Keep Compose UI classes ====================

-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ==================== Keep custom views and components ====================

-keep class space.liushenme.markdownreader.ui.screens.** { *; }
-keep class space.liushenme.markdownreader.ui.components.** { *; }
-keep class space.liushenme.markdownreader.ui.theme.** { *; }
-keep class space.liushenme.markdownreader.ui.system.** { *; }

# ==================== Keep markdown related classes ====================

-keep class space.liushenme.markdownreader.markdown.** { *; }
-keep class space.liushenme.markdownreader.intent.** { *; }
-keep class space.liushenme.markdownreader.navigation.** { *; }

# ==================== General Android Rules ====================

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class * extends androidx.fragment.app.Fragment

# Keep annotation classes
-keep public class * extends java.lang.annotation.Annotation { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== Parcelable ====================

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ==================== Serializable ====================

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== Remove logging in release ====================

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ==================== Keep reflection-heavy classes ====================

-keep class * implements java.lang.reflect.InvocationHandler { *; }
-keepclassmembers class * {
    @java.lang.invoke.MethodHandle <methods>;
}

# ==================== WebView ====================

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, android.webkit.WebResourceRequest);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# ==================== Firebase / Google Play Services (if added later) ====================

# -keep class com.google.android.gms.** { *; }
# -dontwarn com.google.android.gms.**
# -keep class com.google.firebase.** { *; }
# -dontwarn com.google.firebase.**

# ==================== OkHttp / Retrofit (if added later) ====================

# -keepattributes Signature
# -keepattributes Exceptions
# -keep class okhttp3.** { *; }
# -keep interface okhttp3.** { *; }
# -dontwarn okhttp3.**
# -dontwarn okio.**
