import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "net.matsudamper.liteencoder"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.filekit.dialogs.compose)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "net.matsudamper.liteencoder.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "LiteEncoderDesktop"
            packageVersion = "1.0.0"
        }
    }
}
