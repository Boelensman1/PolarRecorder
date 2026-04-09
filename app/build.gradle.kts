// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  dependencies {
    // Override AGP 9's default KGP (2.2.10) with the version from libs.versions.toml.
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
  }
}

plugins { alias(libs.plugins.android.application) apply false }

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")
    publishing.onlyIf { false }
  }
}
