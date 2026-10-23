# Keep Jetpack Compose runtime & models
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.runtime.** { *; }

# Keep ViewModel models and data classes
-keep class com.example.virtualvolumnbutton.ui.viewmodel.** { *; }
-keep class com.example.virtualvolumnbutton.data.model.** { *; }

# Keep AudioStreamType enum and methods
-keepclassmembers enum com.example.virtualvolumnbutton.data.model.AudioStreamType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
