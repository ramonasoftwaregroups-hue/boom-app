# قوانین مربوط به TWA و AndroidX Browser برای جلوگیری از پاک شدن کدها توسط R8
-keep class androidx.browser.** { *; }
-keep class com.google.androidbrowserhelper.** { *; }
-keep class ir.picassooads.boom.twa.** { *; }

-dontwarn androidx.browser.**
-dontwarn com.google.androidbrowserhelper.**
-dontwarn ir.picassooads.boom.twa.**
