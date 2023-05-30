java_library(
    name = "protobuf_java",
    exports = [
        "//external:jar/com/google/protobuf/protobuf_java"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "protobuf_java_util",
    exports = [
        "//external:jar/com/google/protobuf/protobuf_java_util"
    ],
    runtime_deps = [
        "//3rdparty/jvm/com/google/code/findbugs:jsr305",
        "//3rdparty/jvm/com/google/code/gson:gson",
        "//3rdparty/jvm/com/google/errorprone:error_prone_annotations",
        "//3rdparty/jvm/com/google/guava:guava",
        "//3rdparty/jvm/com/google/j2objc:j2objc_annotations",
        ":protobuf_java"
    ],
    visibility = [
        "//visibility:public"
    ]
)


