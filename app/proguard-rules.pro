# The band's transport is the one thing here R8 cannot reason about: the
# native library resolves classes, methods and callbacks by their original JNI
# names, and several of those callbacks have no Kotlin caller to prove them
# reachable. :air ships the keep rule for its own bindings
# (air/consumer-rules.pro) and it comes through with the dependency; this file
# covers what sits on our side of it.

# AirLink and the rest of the Kotlin wrapper hand objects to goldengate and
# take callbacks back from it. Nothing here is renamed rather than work out
# which single methods cross that line: the package is small and it is only
# the release build's size that pays.
-keep class dev.airpulse.fitbit_air.** { *; }

# Entry points Android resolves by name from the manifest. R8 keeps these from
# the manifest already; naming them makes a breakage obvious at build time
# rather than at first launch.
-keep class com.srineere.airbridge.MainActivity { *; }
-keep class com.srineere.airbridge.CalendarRelayService { *; }
-keep class com.srineere.airbridge.CalendarNotificationListener { *; }
-keep class com.srineere.airbridge.RelayBootReceiver { *; }

# The stored config is JSON built by hand, not by a reflective serialiser, so
# nothing below needs keeping for its field names. If that ever changes --
# kotlinx.serialization, Gson, Moshi -- the rule belongs here and the symptom
# will be a release build that reads every setting as its default.
