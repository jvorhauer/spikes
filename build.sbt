import sbtrelease.ReleaseStateTransformations.*

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
val kamon_version = "2.6.3"
val scala_test_version = "3.2.16"

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
      "com.typesafe.akka" %% "akka-actor-typed"            % akka_version,
      "com.typesafe.akka" %% "akka-stream"                 % akka_version,
      "com.typesafe.akka" %% "akka-coordination"           % akka_version,
      "com.typesafe.akka" %% "akka-cluster"                % akka_version,
      "com.typesafe.akka" %% "akka-cluster-tools"          % akka_version,
      "com.typesafe.akka" %% "akka-http"                   % akka_http_version,
      "com.typesafe"      %% "ssl-config-core"             % "0.6.1",
      "com.typesafe.akka" %% "akka-persistence-typed"      % akka_version,
      "com.typesafe.akka" %% "akka-persistence-query"      % akka_version,
      "com.typesafe.akka" %% "akka-persistence-cassandra"  % "1.1.1",
      "com.datastax.oss"  %  "java-driver-core"            % "4.16.0",
      "io.netty"          %  "netty-handler"               % "4.1.94.Final",
      "de.heikoseeberger" %% "akka-http-circe"             % "1.39.2",
    ) ++ Seq(
      "org.scalatest"       %% "scalatest"                % scala_test_version,
      "com.typesafe.akka"   %% "akka-actor-testkit-typed" % akka_version,
      "com.typesafe.akka"   %% "akka-stream-testkit"      % akka_version,
      "com.typesafe.akka"   %% "akka-persistence-testkit" % akka_version,
      "com.typesafe.akka"   %% "akka-http-testkit"        % akka_http_version,
    ).map(_ % "test") ++ Seq(
      "io.scalaland"           %% "chimney"                       % "0.7.5",
      "ch.qos.logback"         %  "logback-classic"               % "1.4.8",
      "io.circe"               %% "circe-generic"                 % "0.14.5",
      "org.wvlet.airframe"     %% "airframe-ulid"                 % "23.7.0",
      "org.owasp.encoder"      %  "encoder"                       % "1.2.3",
      "io.altoo"               %% "akka-kryo-serialization-typed" % "2.5.0",
      "org.scalactic"          %% "scalactic"                     % scala_test_version,
      "org.yaml"               %  "snakeyaml"                     % "2.0",
      "io.lemonlabs"           %% "scala-uri"                     % "4.0.3",
    ) ++ Seq(
      "io.kamon" %% "kamon-bundle"       % kamon_version,
      "io.kamon" %% "kamon-apm-reporter" % kamon_version
    ) ++ Seq(
      "com.michaelpollmeier" %% "gremlin-scala"       % "3.5.3.7",
      "org.apache.tinkerpop" %  "tinkergraph-gremlin" % "3.6.4",
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
    jibTags := List("latest"),
    jibTargetImageCredentialHelper := Some("docker-credential-osxkeychain"),
  )
  .enablePlugins(BuildInfoPlugin).settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.action("buildTime") { java.time.LocalDateTime.now() }
    ),
    buildInfoPackage := "spikes.build"
  )
