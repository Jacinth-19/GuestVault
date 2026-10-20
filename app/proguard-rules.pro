# Keep app classes (Hilt generated code relies on reflection-free injection,
# but keep the rule file for future minified builds).
-keep class com.guestvault.** { *; }
-keepattributes SourceFile,LineNumberTable
