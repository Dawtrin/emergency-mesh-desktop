# Protocol fields are accessed by Gson through @SerializedName.  Keep the
# model names as well so release builds remain easy to inspect in a demo.
-keep class com.rescue.mesh.android.protocol.** { *; }
-keep class com.rescue.mesh.android.data.** { *; }
