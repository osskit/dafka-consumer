java_library(
    name = "kotlin_stdlib",
    exports = [
        "//external:jar/org/jetbrains/kotlin/kotlin_stdlib"
    ],
    runtime_deps = [
        "//3rdparty/jvm/org/jetbrains:annotations",
        ":kotlin_stdlib_common"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)



java_library(
    name = "kotlin_stdlib_common",
    exports = [
        "//external:jar/org/jetbrains/kotlin/kotlin_stdlib_common"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)



java_library(
    name = "kotlin_stdlib_jdk7",
    exports = [
        "//external:jar/org/jetbrains/kotlin/kotlin_stdlib_jdk7"
    ],
    runtime_deps = [
        ":kotlin_stdlib"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)



java_library(
    name = "kotlin_stdlib_jdk8",
    exports = [
        "//external:jar/org/jetbrains/kotlin/kotlin_stdlib_jdk8"
    ],
    runtime_deps = [
        ":kotlin_stdlib",
        ":kotlin_stdlib_jdk7"
    ],
    visibility = [
        "//3rdparty/jvm:__subpackages__"
    ]
)


