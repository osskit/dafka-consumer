java_library(
    name = "logback_jackson",
    exports = [
        "//external:jar/ch/qos/logback/contrib/logback_jackson"
    ],
    runtime_deps = [
        ":logback_json_core"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "logback_json_classic",
    exports = [
        "//external:jar/ch/qos/logback/contrib/logback_json_classic"
    ],
    runtime_deps = [
        "//3rdparty/jvm/ch/qos/logback:logback_classic",
        ":logback_json_core"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "logback_json_core",
    exports = [
        "//external:jar/ch/qos/logback/contrib/logback_json_core"
    ],
    runtime_deps = [
        "//3rdparty/jvm/ch/qos/logback:logback_core"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)


