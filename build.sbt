import sbtrelease.ReleaseStateTransformations._

ThisBuild / scalaVersion           := "2.13.11"
ThisBuild / organization           := "nl.miruvor"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := "4.7.8"
ThisBuild / scalafixScalaBinaryVersion := "2.13"

ThisBuild / parallelExecution := false

Compile / compileOrder := CompileOrder.JavaThenScala
Test / compileOrder    := CompileOrder.JavaThenScala

val akka_version = "2.8.3"
val akka_http_version = "10.5.2"
val akka_proj_version = "1.4.2"
val kamon_version = "2.6.3"
val scala_test_version = "3.2.16"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val root = (project in file("."))
  .settings(
    name := "spikes",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-Xsource:3",
      "-deprecation", "-feature", "-explaintypes",
      "-language:implicitConversions", "-language:existentials", "-language:higherKinds", "-language:postfixOps",
      "-unchecked", "-Xcheckinit",
      "-Xlint:infer-any", "-Xlint:inaccessible", "-Xlint:missing-interpolator", "-Xlint:private-shadow",
      "-Xlint:type-parameter-shadow", "-Xlint:unused",
      "-Wunused:implicits", "-Wunused:imports", "-Wunused:locals", "-Wunused:params",
      "-Yrangepos"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-actor-typed"             % akka_version,
      "com.typesafe.akka"  %% "akka-stream"                  % akka_version,
      "com.typesafe.akka"  %% "akka-coordination"            % akka_version,
      "com.typesafe.akka"  %% "akka-cluster"                 % akka_version,
      "com.typesafe.akka"  %% "akka-cluster-tools"           % akka_version,
      "com.typesafe.akka"  %% "akka-http"                    % akka_http_version,
      "com.typesafe"       %% "ssl-config-core"              % "0.6.1",
      "com.typesafe.akka"  %% "akka-persistence-typed"       % akka_version,
      "com.typesafe.akka"  %% "akka-persistence-query"       % akka_version,
      "com.typesafe.akka"  %% "akka-persistence-cassandra"   % "1.1.1",
      "com.lightbend.akka" %% "akka-projection-core"         % akka_proj_version,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % akka_proj_version,
      "com.lightbend.akka" %% "akka-projection-cassandra"    % akka_proj_version,
    ) ++ Seq(
      "org.scalatest"       %% "scalatest"                % scala_test_version,
      "com.typesafe.akka"   %% "akka-actor-testkit-typed" % akka_version,
      "com.typesafe.akka"   %% "akka-stream-testkit"      % akka_version,
      "com.typesafe.akka"   %% "akka-persistence-testkit" % akka_version,
      "com.typesafe.akka"   %% "akka-http-testkit"        % akka_http_version,
      "com.lightbend.akka"  %% "akka-projection-testkit"  % akka_proj_version,
    ).map(_ % "test") ++ Seq(
      "io.scalaland"           %% "chimney"                       % "0.7.5",
      "io.circe"               %% "circe-generic"                 % "0.14.5",
      "de.heikoseeberger"      %% "akka-http-circe"               % "1.39.2",
      "org.wvlet.airframe"     %% "airframe-ulid"                 % "23.7.4",
      "io.altoo"               %% "akka-kryo-serialization-typed" % "2.5.1",
      "io.lemonlabs"           %% "scala-uri"                     % "4.0.3",
    ) ++ Seq(
      "com.datastax.oss"  % "java-driver-core" % "4.17.0",
      "io.netty"          % "netty-handler"    % "4.1.96.Final",
      "org.owasp.encoder" % "encoder"          % "1.2.3",
      "org.yaml"          % "snakeyaml"        % "2.0",
      "ch.qos.logback"    % "logback-classic"  % "1.4.8",
    ) ++ Seq(
      "io.kamon" %% "kamon-bundle"       % kamon_version,
      "io.kamon" %% "kamon-apm-reporter" % kamon_version
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
    jibJvmFlags := List("-XX:+UseCGroupMemoryLimitForHeap"),
    jibTags := List("latest"),
    jibTargetImageCredentialHelper := Some("docker-credential-osxkeychain"),
  )
  .enablePlugins(BuildInfoPlugin).settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, BuildInfoKey.action("buildTime") { java.time.LocalDateTime.now() }),
    buildInfoPackage := "spikes.build"
  )
