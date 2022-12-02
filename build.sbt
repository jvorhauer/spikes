ThisBuild / version := "1.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val AkkaVersion = "2.7.0"
lazy val AkkaHttpVersion = "10.4.0"
lazy val ScalaTestVersion = "3.2.14"

lazy val root = (project in file("."))
  .settings(
    name := "spikes",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-language:existentials",
      "-Xlint:infer-any",
      "-Xlint:inaccessible",
      "-Xlint:missing-interpolator",
      "-Xlint:private-shadow",
      "-Xlint:type-parameter-shadow",
      "-Xlint:unused"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"           % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream"                % AkkaVersion,
      "com.typesafe.akka" %% "akka-http"                  % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-persistence-typed"     % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.1.0",
      "de.heikoseeberger" %% "akka-http-circe"            % "1.39.2",
      "io.circe"          %% "circe-generic"              % "0.14.3"
    ) ++ Seq(
      "org.scalactic"     %% "scalactic"           % ScalaTestVersion,
      "org.scalatest"     %% "scalatest"           % ScalaTestVersion % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion      % "test",
      "com.typesafe.akka" %% "akka-http-testkit"   % AkkaHttpVersion  % "test"
    ) ++ Seq(
      "io.scalaland" %% "chimney"      % "0.6.2",
      "org.slf4j"    %  "slf4j-simple" % "2.0.4"
    )
  )
