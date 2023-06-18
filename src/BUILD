load("@io_bazel_rules_docker//java:image.bzl", "java_image")
load("@io_bazel_rules_docker//container:container.bzl", "container_image")
load("@io_bazel_rules_docker//container:container.bzl", "container_bundle")

java_library(
    name = "lib",
    srcs = glob(["main/java/**/*.java"]),
    resources = glob(["main/resources/*"]),
    deps = [
        "@third_party//3rdparty/jvm/ch/qos/logback:logback_core",
        "@third_party//3rdparty/jvm/ch/qos/logback/contrib:logback_jackson",
        "@third_party//3rdparty/jvm/ch/qos/logback/contrib:logback_json_classic",
        "@third_party//3rdparty/jvm/com/fasterxml/jackson/core:jackson_databind",
        "@third_party//3rdparty/jvm/com/google/guava",
        "@third_party//3rdparty/jvm/com/google/protobuf:protobuf_java",
        "@third_party//3rdparty/jvm/com/google/protobuf:protobuf_java_util",
        "@third_party//3rdparty/jvm/com/spotify:futures_extra",
        "@third_party//3rdparty/jvm/com/squareup/okhttp3:okhttp",
        "@third_party//3rdparty/jvm/dev/failsafe",
        "@third_party//3rdparty/jvm/dev/failsafe:failsafe_okhttp",
        "@third_party//3rdparty/jvm/io/github/cdimascio:java_dotenv",
        "@third_party//3rdparty/jvm/io/projectreactor:reactor_core",
        "@third_party//3rdparty/jvm/io/projectreactor/addons:reactor_extra",
        "@third_party//3rdparty/jvm/io/prometheus:simpleclient",
        "@third_party//3rdparty/jvm/io/prometheus:simpleclient_hotspot",
        "@third_party//3rdparty/jvm/io/prometheus:simpleclient_httpserver",
        "@third_party//3rdparty/jvm/org/apache/kafka:kafka_clients",
        "@third_party//3rdparty/jvm/org/jetbrains:annotations",
        "@third_party//3rdparty/jvm/org/json",
        "@third_party//3rdparty/jvm/org/reactivestreams:reactive_streams",
        "@third_party//3rdparty/jvm/org/slf4j:slf4j_api",
    ],
)

java_image(
    name = "image",
    base = "@java_base_image//image",
    main_class = "Main",
    runtime_deps = [":lib"],
)
