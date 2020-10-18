name := "BenchRestTask"

version := "0.1"

scalaVersion := "2.13.3"

val sttpVersion = "2.2.9"
val akkaVersion = "2.5.31"
val scalaTestVersion = "3.2.0"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client" %% "core",
  "com.softwaremill.sttp.client" %% "circe",
  "com.softwaremill.sttp.client" %% "akka-http-backend",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future"
).map(_ % sttpVersion) ++ Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "org.scalatest" %% "scalatest-wordspec" % scalaTestVersion % "test"
)


scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-explaintypes",
  "-language:_"
)