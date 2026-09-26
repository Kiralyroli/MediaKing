# FFmpeg extension is loaded through JNI and looked up reflectively by DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Ktor's IntelliJ debugger detection references JVM management classes that Android does not have;
# the code path never runs on a device.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
