#测试时候打开,主要为了方便定位
#-keepattributes SourceFile,LineNumberTable
#保留行号,源文件名抹掉
-keepattributes LineNumberTable

#这将使ProGuard更积极地重载相似的方法，从而减小方法和字段的总数。
-overloadaggressively
#这可以让ProGuard修改类的访问级别，从而进一步减少代码的大小。
-allowaccessmodification
#资源文件压缩
#调整资源文件的内容，使其与混淆的代码、字段、方法名等一致。
-adaptresourcefilecontents
-adaptresourcefilenames

#指定代码的压缩级别(默认是5这步骤在感染过程中非常耗时,切成1)
-optimizationpasses 5
#将没用的类都放到这个里面,我们本身的核心类不混淆。

#通过MT进行混淆
-repackageclasses 'MagiskRuntime'

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** e(...);
    public static *** i(...);
    public static *** v(...);
    public static *** println(...);
    public static *** w(...);
    public static *** wtf(...);
}


