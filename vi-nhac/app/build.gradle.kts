plugins { id("com.android.application") }

android {
    namespace = "titus.expenseassistant"
    compileSdk = 35

    val signingPath = providers.gradleProperty("trolySigningFile").orNull
    val signingStorePassword = providers.gradleProperty("trolySigningStorePassword").orNull
    val signingKeyAlias = providers.gradleProperty("trolySigningKeyAlias").orNull
    val signingKeyPassword = providers.gradleProperty("trolySigningKeyPassword").orNull

    signingConfigs {
        create("trolyRelease") {
            if (signingPath != null && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
                storeFile = file(signingPath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "titus.expenseassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.6.0"
    }

    testOptions { unitTests.isIncludeAndroidResources = false }

    buildTypes {
        getByName("release") {
            if (signingPath != null && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
                signingConfig = signingConfigs.getByName("trolyRelease")
            }
        }
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
