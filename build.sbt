// ============================================================
// Analytics Service — Сервис аналитики и отчётов
// ============================================================

name := "analytics-service"
version := "1.0.0"
scalaVersion := "3.4.0"

// Опции компилятора Scala 3
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions"
)

// ============================================================
// Версии зависимостей
// ============================================================

val zioVersion       = "2.0.20"
val zioHttpVersion   = "3.0.0-RC4"
val zioConfigVersion = "4.0.0-RC16"
val zioJsonVersion   = "0.6.2"
val doobieVersion    = "1.0.0-RC4"
val logbackVersion   = "1.4.14"

// ============================================================
// Зависимости
// ============================================================

libraryDependencies ++= Seq(
  // ZIO Core
  "dev.zio" %% "zio"         % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,

  // ZIO HTTP (REST API: отчёты, экспорт, расписания, health)
  "dev.zio" %% "zio-http" % zioHttpVersion,

  // ZIO Config (типизированная конфигурация из application.conf)
  "dev.zio" %% "zio-config"          % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,

  // ZIO JSON (сериализация отчётов и API)
  "dev.zio" %% "zio-json" % zioJsonVersion,

  // ZIO Interop Cats (мост ZIO ↔ cats-effect для Doobie)
  "dev.zio" %% "zio-interop-cats" % "23.1.0.0",

  // Doobie (TimescaleDB + PostgreSQL доступ)
  "org.tpolecat" %% "doobie-core"     % doobieVersion,
  "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,

  // PostgreSQL JDBC Driver
  "org.postgresql" % "postgresql" % "42.7.1",

  // Connection Pool
  "com.zaxxer" % "HikariCP" % "5.1.0",

  // Apache POI — генерация Excel (.xlsx)
  "org.apache.poi" % "poi"       % "5.2.5",
  "org.apache.poi" % "poi-ooxml" % "5.2.5",

  // OpenPDF — генерация PDF
  "com.github.librepdf" % "openpdf" % "1.3.35",

  // AWS S3 SDK — хранилище файлов (S3 / MinIO)
  "software.amazon.awssdk" % "s3"        % "2.24.5",
  "software.amazon.awssdk" % "netty-nio-client" % "2.24.5",

  // Cron парсер для расписаний
  "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.7.0",

  // Логирование
  "ch.qos.logback"         % "logback-classic"      % logbackVersion,
  "ch.qos.logback.contrib" % "logback-json-classic"  % "0.1.5",
  "ch.qos.logback.contrib" % "logback-jackson"       % "0.1.5",
  "com.fasterxml.jackson.core" % "jackson-databind"  % "2.16.1",

  // Тестирование
  "dev.zio" %% "zio-test"          % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt"      % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
)

// Test framework
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// Assembly plugin settings
assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf"              => MergeStrategy.concat
  case x                             => MergeStrategy.first
}
