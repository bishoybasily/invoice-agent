plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.1.0"
description = "PDF invoice -> LLM extraction -> deterministic validation -> review queue -> XRechnung"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}


dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

//    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mariadb")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.8")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    val samplesDir = providers.environmentVariable("OPS_SAMPLES_DIR").orElse("../invoice-agent-samples")
    systemProperty("ops.samples-dir", file(samplesDir.get()).absolutePath)
}
