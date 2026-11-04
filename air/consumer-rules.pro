# The externally loaded native transport resolves these classes and callbacks
# by their original JNI names, including callbacks without native declarations.
#
# Two packages are excluded, and the evidence is reproducible rather than
# assumed. Every class the native code looks up appears as a literal string in
# the library, so the list it actually resolves can be read straight out:
#
#   strings -a air/src/main/jniLibs/arm64-v8a/libxp.so \
#     | grep -o "com/fitbit/goldengate/bindings/[a-zA-Z/]*" | sort -u
#
# That prints classes under coap/, dtls/, node/ and stack/ and nothing else.
# bindings/remote/ is upstream's WebSocket debug shell and bindings/services/
# its Blast and CoAP test services; this app starts neither, and the native
# side never asks for them. Excluding the two saves about 190 KB of the
# release APK -- a twentieth of it -- and lets R8 drop OkHttp's code with
# them.
#
# Before narrowing this further, re-run the command above. Before widening it
# back, know that a release build which cannot reach the band fails at the
# first CoAP call and not at compile time.
-keep class !com.fitbit.goldengate.bindings.remote.**,!com.fitbit.goldengate.bindings.services.**,com.fitbit.goldengate.bindings.** { *; }
