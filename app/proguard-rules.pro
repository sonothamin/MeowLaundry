# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sonothamin.meowlaundry.**$$serializer { *; }
-keepclassmembers class com.sonothamin.meowlaundry.** {
    *** Companion;
}
-keepclasseswithmembers class com.sonothamin.meowlaundry.** {
    kotlinx.serialization.KSerializer serializer(...);
}
