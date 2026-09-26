# FFmpeg extension is loaded through JNI and looked up reflectively by DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Ktor's IntelliJ debugger detection references JVM management classes that Android does not have;
# the code path never runs on a device.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# smbj (network folders). Kerberos and mbassador's EL filters are optional parts it never loads here.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.**
# mbassador finds event handlers by reflection, and smbj wires its packet handling through them.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * { @net.engio.mbassy.listener.Handler <methods>; }
