plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("real-runners")
    }
}

tasks.register<JavaExec>("realRunnersPromptCheck") {
    description = "Runs interactive real-environment runner checks (prompts for credentials and endpoints)."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("wellatleastitried.mediaguard.service.RealRunnersInteractiveCheck")
    standardInput = System.`in`
    dependsOn(tasks.testClasses)
}

tasks.register<Test>("realRunnersEnvTest") {
    description = "Runs env-backed real runner integration test using .env.test or .env values."
    group = "verification"
    useJUnitPlatform {
        includeTags("real-runners")
    }
}
