ThisBuild / version := "1.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"
val ScalaTestVersion = "3.2.14"
val AccordVersion = "0.7.6"

lazy val root = (project in file("."))
  .settings(
    name := "spikes",
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
      "com.wix"           %% "accord-scalatest"    % AccordVersion    % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion      % "test",
      "com.typesafe.akka" %% "akka-http-testkit"   % AkkaHttpVersion  % "test"
    ) ++ Seq(
      "io.scalaland" %% "chimney"      % "0.6.2",
      "com.wix"      %% "accord-core"  % AccordVersion,
      "org.slf4j"    %  "slf4j-simple" % "2.0.4"
    )
  )
