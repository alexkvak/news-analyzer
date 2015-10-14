//enablePlugins(JavaAppPackaging)

name         := "news-analyzer"

organization := "com.aks-studio"

version      := "0.1"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.3.9"
  val akkaStreamV = "1.0"
  val scalaTestV  = "2.2.1"
  Seq(
    "com.github.nscala-time" %% "nscala-time" % "1.8.0",
    "com.typesafe.akka" %% "akka-actor"                        % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"       % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"            % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"    % akkaStreamV,
    "org.scalatest"     %% "scalatest"                         % scalaTestV % "test"
  )
}

//Revolver.settings
