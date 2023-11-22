java_library(
    name = "micrometer_commons",
    exports = [
        "//external:jar/io/micrometer/micrometer_commons"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)



java_library(
    name = "micrometer_observation",
    exports = [
        "//external:jar/io/micrometer/micrometer_observation"
    ],
    runtime_deps = [
        ":micrometer_commons"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)


