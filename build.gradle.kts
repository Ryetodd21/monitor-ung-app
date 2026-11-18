// Root build script — declares plugins at version-catalog scope.
// All versions live in `gradle/libs.versions.toml`. Subprojects apply via
// `alias(libs.plugins.xxx)` in their own `build.gradle.kts`.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.cyclonedx) apply false
}
