#主要为了混淆runtime里面的核心逻辑

#混淆字典
-obfuscationdictionary dictionary_rules.txt
-classobfuscationdictionary dictionary_rules.txt
-packageobfuscationdictionary dictionary_rules.txt

#忽略警告
-ignorewarnings
-dontwarn

#指定代码的压缩级别(默认是5这步骤在感染过程中非常耗时,切成1)
-optimizationpasses 1


# FastJson
-dontwarn com.alibaba.fastjson.**
-keep class com.alibaba.fastjson.** { *; }




# xposed作为插件API，需要keep
-keep ,allowoptimization class de.robv.android.xposed.**{*;}
-keep ,allowoptimization class external.org.apache.commons.**{*;}
-keep ,allowoptimization class android.content.pm.**{*;}





