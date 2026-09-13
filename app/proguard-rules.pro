# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.jamesnicholls.memoswidget.**$$serializer { *; }
-keepclassmembers class dev.jamesnicholls.memoswidget.** { *** Companion; }
-keepclasseswithmembers class dev.jamesnicholls.memoswidget.** { kotlinx.serialization.KSerializer serializer(...); }
