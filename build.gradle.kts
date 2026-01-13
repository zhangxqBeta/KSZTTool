plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.10.1"
}

group = "com.zhangxq"
version = "3.4.15"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi:5.2.2")
    implementation("org.apache.poi:poi-ooxml:5.2.2")
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    version.set("2023.3")
    type.set("IC")

    // 依赖的插件
    plugins.set(listOf())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("252.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
