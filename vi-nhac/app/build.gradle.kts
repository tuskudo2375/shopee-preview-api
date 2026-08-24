plugins { id("com.android.application") }

android {
    namespace = "titus.expenseassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "titus.expenseassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    testOptions { unitTests.isIncludeAndroidResources = false }
}

dependencies { testImplementation("junit:junit:4.13.2") }
