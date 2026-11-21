pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val sdkRepo = providers.gradleProperty("sdkRepo").orNull
    ?: error("Specify an absolute SDK repository with -PsdkRepo=/absolute/path")
val sherpaRepo = providers.gradleProperty("sherpaRepo").orNull
    ?: error("Specify an absolute Sherpa repository with -PsherpaRepo=/absolute/path")

require(java.io.File(sdkRepo).isAbsolute) {
    "-PsdkRepo must be an absolute path: $sdkRepo"
}
require(java.io.File(sherpaRepo).isAbsolute) {
    "-PsherpaRepo must be an absolute path: $sherpaRepo"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "sdkRepo"
            url = uri(sdkRepo)
            content {
                includeGroup("io.ondeviceai")
                excludeGroup("com.k2fsa.sherpa.onnx")
            }
        }
        maven {
            name = "sherpaRepo"
            url = uri(sherpaRepo)
            content {
                includeGroup("com.k2fsa.sherpa.onnx")
                excludeGroup("io.ondeviceai")
            }
        }
        google {
            content {
                excludeGroup("io.ondeviceai")
                excludeGroup("com.k2fsa.sherpa.onnx")
            }
        }
        mavenCentral {
            content {
                excludeGroup("io.ondeviceai")
                excludeGroup("com.k2fsa.sherpa.onnx")
            }
        }
    }
}

rootProject.name = "w3-sherpa-notice-consumer"
