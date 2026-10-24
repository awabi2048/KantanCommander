import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
}

group = "me.awabi2048"
version = "26.1024.1"

repositories {
    mavenLocal()
    maven { url = uri("../.m2-paper26-kotlin2320") }
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

// pom.xml の provided スコープ相当（compileOnly へ配置し、テストのコンパイル・実行双方で見えるよう testImplementation にも追加）
val providedDeps = listOf(
    "io.papermc.paper:paper-api:26.2.build.129-stable",
    "org.jetbrains.kotlin:kotlin-stdlib:2.3.20",
    "com.awabi2048:CC-System:26.1024.1",
    "net.dmulloy2:ProtocolLib:5.4.0",
    "com.moulberry.axiom:AxiomPaper:5.0.1",
    "awabi2048:my-world-manager:26.905.2",
    "me.awabi2048:MWM-Chanpon:26.905.2",
    "jp.awabi2048:CC-Content:26.907.1",
    "org.geysermc.geyser:api:2.10.1",
)

dependencies {
    providedDeps.forEach {
        compileOnly(it)
        testImplementation(it)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

// .kcp エディタ拡張機能へ供給する語彙カタログ（dsl-catalog.json）を
// Kotlin 側の語彙正本から生成します。
tasks.register<JavaExec>("generateDslCatalog") {
    group = "documentation"
    description = "editors/vscode-kcp/data/dsl-catalog.json を生成します"
    dependsOn(tasks.compileTestKotlin)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.awabi2048.kantancommander.tools.DslCatalogGenerator")
    args = listOf(layout.projectDirectory.file("editors/vscode-kcp/data/dsl-catalog.json").asFile.absolutePath)
}

// pom.xml の resources filtering 相当（${project.version} を展開）
tasks.processResources {
    val versionString = project.version.toString()
    inputs.property("projectVersion", versionString)
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("version" to versionString)))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // pom.xml の shade フィルタ相当（依存JAR含め MANIFEST.MF を除外）
    exclude("META-INF/MANIFEST.MF")
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifactId = "KantanCommander"
            from(components["shadow"])
        }
    }
    repositories {
        maven {
            name = "workspace"
            url = uri("../.m2-paper26-kotlin2320")
        }
    }
}
