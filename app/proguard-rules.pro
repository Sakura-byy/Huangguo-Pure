# Huangguo Pure — R8 规则
# 代码全部为直接引用（无反射/无序列化类），okhttp 与 media3 自带 consumer rules；
# 这里只补 dontwarn，避免第三方告警中断构建。

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.support.**

# 崩溃堆栈可读
-keepattributes SourceFile, LineNumberTable, Signature, InnerClasses, EnclosingMethod
-renamesourcefileattribute SourceFile
