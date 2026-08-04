load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_tarball")
load("@rules_pkg//pkg:tar.bzl", "pkg_tar")

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
        "@third_party//3rdparty/jvm/com/google/code/gson:gson",
        "@third_party//3rdparty/jvm/com/google/protobuf:protobuf_java",
        "@third_party//3rdparty/jvm/com/google/protobuf:protobuf_java_util",
        "@third_party//3rdparty/jvm/com/spotify:futures_extra",
        "@third_party//3rdparty/jvm/com/squareup/okhttp3:okhttp",
        "@third_party//3rdparty/jvm/com/squareup/okio",
        "@third_party//3rdparty/jvm/com/squareup/okio:okio_jvm",
        "@third_party//3rdparty/jvm/dev/failsafe",
        "@third_party//3rdparty/jvm/dev/failsafe:failsafe_okhttp",
        "@third_party//3rdparty/jvm/io/github/cdimascio:java_dotenv",
        "@third_party//3rdparty/jvm/io/projectreactor:reactor_core",
        "@third_party//3rdparty/jvm/io/projectreactor/kafka:reactor_kafka",
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

java_binary(
    name = "main",
    main_class = "Main",
    runtime_deps = [":lib"],
)

copy_file(
    name = "app_jar",
    src = ":main_deploy.jar",
    out = "dafka-consumer.jar",
)

pkg_tar(
    name = "app_layer",
    srcs = [":app_jar"],
    package_dir = "/app",
)

oci_image(
    name = "image",
    base = "@java_base_image",
    entrypoint = [
        "java",
        "-cp",
        "/app/dafka-consumer.jar",
        "Main",
    ],
    env = {"JAVA_RUNFILES": "/app"},
    tars = [":app_layer"],
)

oci_tarball(
    name = "image_tarball",
    image = ":image",
    repo_tags = ["bazel/src:image"],
)

# `//src:image.tar` in `bazel-bin/src/` is the contract the release workflow and
# the `pretest` script rely on; oci_tarball writes to `image_tarball/tarball.tar`.
copy_file(
    name = "image_tar",
    src = ":image_tarball",
    out = "image.tar",
)
