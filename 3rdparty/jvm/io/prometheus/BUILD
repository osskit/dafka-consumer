java_library(
    name = "simpleclient",
    exports = [
        "//external:jar/io/prometheus/simpleclient"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "simpleclient_common",
    exports = [
        "//external:jar/io/prometheus/simpleclient_common"
    ],
    runtime_deps = [
        ":simpleclient"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)



java_library(
    name = "simpleclient_hotspot",
    exports = [
        "//external:jar/io/prometheus/simpleclient_hotspot"
    ],
    runtime_deps = [
        ":simpleclient"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "simpleclient_httpserver",
    exports = [
        "//external:jar/io/prometheus/simpleclient_httpserver"
    ],
    runtime_deps = [
        ":simpleclient",
        ":simpleclient_common"
    ],
    visibility = [
        "//visibility:public"
    ]
)


