java_library(
    name = "failsafe",
    exports = [
        "//external:jar/dev/failsafe/failsafe"
    ],
    visibility = [
        "//visibility:public"
    ]
)



java_library(
    name = "failsafe_okhttp",
    exports = [
        "//external:jar/dev/failsafe/failsafe_okhttp"
    ],
    runtime_deps = [
        "//3rdparty/jvm/com/squareup/okhttp3:okhttp",
        ":failsafe"
    ],
    visibility = [
        "//visibility:public"
    ]
)


