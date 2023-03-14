import ReleaseTransformations.*

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / organization := "nl.miruvor"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := "2.13"

lazy val root = (project in file("."))
  .settings(
    name := "spikes",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-Xsource:3",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-explaintypes",
      "-language:implicitConversions",
      "-language:existentials",
      "-language:higherKinds",
      "-language:postfixOps",
      "-Xlint:infer-any",
      "-Xlint:inaccessible",
      "-Xlint:missing-interpolator",
      "-Xlint:private-shadow",
      "-Xlint:type-parameter-shadow",
      "-Xlint:unused"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"            % "2.7.0",
      "com.typesafe.akka" %% "akka-stream"                 % "2.7.0",
      "com.typesafe.akka" %% "akka-http"                   % "10.5.0",
      "com.typesafe"      %% "ssl-config-core"             % "0.6.1",
      "com.typesafe.akka" %% "akka-persistence-typed"      % "2.7.0",
      "com.typesafe.akka" %% "akka-persistence-query"      % "2.7.0",
      "com.typesafe.akka" %% "akka-persistence-cassandra"  % "1.1.0",
      "de.heikoseeberger" %% "akka-http-circe"             % "1.39.2",
    ) ++ Seq(
      "org.scalatest"       %% "scalatest"                % "3.2.15",
      "com.typesafe.akka"   %% "akka-actor-testkit-typed" % "2.7.0",
      "com.typesafe.akka"   %% "akka-stream-testkit"      % "2.7.0",
      "com.typesafe.akka"   %% "akka-persistence-testkit" % "2.7.0",
      "com.typesafe.akka"   %% "akka-http-testkit"        % "10.5.0",
      "net.datafaker"       %  "datafaker"                % "1.8.0",
      "com.danielasfregola" %% "random-data-generator"    % "2.9",
    ).map(_ % "test") ++ Seq(
      "io.scalaland"           %% "chimney"                       % "0.7.1",
      "ch.qos.logback"         %  "logback-classic"               % "1.4.5",
      "io.circe"               %% "circe-generic"                 % "0.14.5",
      "org.wvlet.airframe"     %% "airframe-ulid"                 % "23.3.0",
      "org.scala-lang.modules" %% "scala-collection-contrib"      % "0.3.0",
      "org.owasp.encoder"      %  "encoder"                       % "1.2.3",
      "io.altoo"               %% "akka-kryo-serialization-typed" % "2.5.0",
      "org.scalactic"          %% "scalactic"                     % "3.2.15",
      "org.yaml"               %  "snakeyaml"                     % "2.0",
    ) ++ Seq(
      "io.kamon" %% "kamon-bundle"       % "2.6.0",
      "io.kamon" %% "kamon-apm-reporter" % "2.6.0"
    ),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies, inquireVersions,
      runClean, runTest,
      setReleaseVersion, commitReleaseVersion, tagRelease,
      setNextVersion, commitNextVersion, pushChanges
    ),
    jibBaseImage := "openjdk:17",
    jibRegistry := "ghcr.io",
    jibCustomRepositoryPath := Some("jvorhauer/spikes"),
    jibTcpPorts := List(8080),
    jibUseCurrentTimestamp := true,
    jibName := "spikes",
    jibTargetImageCredentialHelper := Some("docker-credential-osxkeychain")
  )
