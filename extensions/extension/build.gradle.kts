extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
}

dependencies {
    // Curve 5.63.0 already ships this version; never bundle a second AndroidX copy.
    compileOnly("androidx.appcompat:appcompat:1.8.0")
}
