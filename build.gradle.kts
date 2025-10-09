plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "f1.betting.poc"
version = "0.0.1-SNAPSHOT"
description = "POC for betting on historical F1 events"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

extra["springModulithVersion"] = "1.4.1"

// ✅ Common configuration for all subprojects
subprojects {
	apply(plugin = "java")
	apply(plugin = "io.spring.dependency-management")

	repositories {
		mavenCentral()
	}

	dependencies {
		val lombokVersion = "1.18.32" // latest stable as of now

		compileOnly("org.projectlombok:lombok:$lombokVersion")
		annotationProcessor("org.projectlombok:lombok:$lombokVersion")

		implementation("org.mapstruct:mapstruct:1.5.5.Final")                    // adjust MapStruct version to match Boot
		annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
	}

	dependencyManagement {
		imports {
			mavenBom("org.springframework.modulith:spring-modulith-bom:${rootProject.extra["springModulithVersion"]}")
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}