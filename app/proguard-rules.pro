# R8: stack traces stay readable; the mapping file is written to app/build/outputs/mapping/release/.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ViewModels and workers are created by reflection through these constructors.
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Enum names are persisted in preferences and restored with valueOf.
-keepclassmembers enum fi.omasaasahko.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
