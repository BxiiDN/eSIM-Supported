# Model JSON (Gson) dan entitas Room dipertahankan apa adanya.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.bxiidn.esimmanager.provider.** { *; }
-keep class com.bxiidn.esimmanager.data.db.** { *; }

# Retrofit + coroutine (suspend) di mode R8 penuh.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson TypeToken.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Dependensi opsional yang tidak ada di Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
