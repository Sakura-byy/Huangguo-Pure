# Huangguo Pure — R8 规则
# 代码全部为直接引用（无反射/无序列化类），okhttp / media3 / Compose 均自带
# consumer rules；这里只补 dontwarn 与最必要的一条 keep，避免告警中断构建。

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.support.**
-dontwarn io.github.kyant0.**

# 崩溃堆栈可读
-keepattributes SourceFile, LineNumberTable, Signature, InnerClasses, EnclosingMethod
-renamesourcefileattribute SourceFile

# 全部类塞进同一个包：类名字符串在 dex 里只存一次，省几十 KB
-repackageclasses 'o'

# FileProvider 由 manifest 声明、系统按类名实例化
-keep class androidx.core.content.FileProvider { *; }

# 日志调用整段删掉（含字符串拼接）：release 里没人看，dex 与运行期都省
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
