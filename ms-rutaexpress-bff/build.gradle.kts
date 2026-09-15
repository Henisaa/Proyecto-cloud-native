plugins {
	kotlin("jvm") version "2.1.10"
	kotlin("plugin.spring") version "2.1.10"
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("eclipse")
}

group = "com.rutaexpress"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

repositories {
	mavenCentral()
}

dependencies {
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Web, Validación & Seguridad OAuth2 Resource Server (Azure AD JWT)
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

	// Caché local Caffeine
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")

	// Observabilidad y Métricas Prometheus
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")

	// Documentación OpenAPI / Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

	// Pruebas automatizadas
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<JavaCompile> {
	options.release.set(21)
}

tasks.withType<Test> {
	useJUnitPlatform()
}
