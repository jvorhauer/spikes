import sbtrelease.ReleaseStateTransformations._

ThisBuild / scalaVersion           := "2.13.12"
ThisBuild / organization           := "nl.miruvor"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := "4.7.8"
ThisBuild / scalafixScalaBinaryVersion := "2.13"

ThisBuild / Test / logBuffered := false
ThisBuild / Test / parallelExecution := false

val versions = new {
  val akka = "2.8.4"
  val http = "10.5.2"
  val kamon = "2.6.3"
  val scalaTest = "3.2.17"
  val jdbc = "4.0.0"
  val sentry = "6.29.0"
}

resolvers ++= Seq(
  "Akka library repository".at("https://repo.akka.io/maven"),
  "Artima Maven Repository".at("https://repo.artima.com/releases")
) ++ Resolver.sonatypeOssRepos("snapshots")

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
    javacOptions ++= Seq("-parameters"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-actor-typed"           % versions.akka,
      "com.typesafe.akka"  %% "akka-stream"                % versions.akka,
      "com.typesafe.akka"  %% "akka-coordination"          % versions.akka,
      "com.typesafe.akka"  %% "akka-cluster"               % versions.akka,
      "com.typesafe.akka"  %% "akka-cluster-tools"         % versions.akka,
      "com.typesafe.akka"  %% "akka-http"                  % versions.http,
      "com.typesafe"       %% "ssl-config-core"            % "0.6.1",
      "com.typesafe.akka"  %% "akka-persistence-typed"     % versions.akka,
      "com.typesafe.akka"  %% "akka-persistence-cassandra" % "1.1.1",
    ) ++ Seq(
      "org.scalatest"       %% "scalatest"                % versions.scalaTest,
      "com.typesafe.akka"   %% "akka-actor-testkit-typed" % versions.akka,
      "com.typesafe.akka"   %% "akka-stream-testkit"      % versions.akka,
      "com.typesafe.akka"   %% "akka-persistence-testkit" % versions.akka,
      "com.typesafe.akka"   %% "akka-http-testkit"        % versions.http,
      "org.scalikejdbc"     %% "scalikejdbc-test"         % versions.jdbc,
      "org.specs2"          %% "specs2-core"              % "4.20.2"
    ).map(_ % "test") ++ Seq(
      "io.circe"           %% "circe-generic"                 % "0.14.6",
      "de.heikoseeberger"  %% "akka-http-circe"               % "1.39.2",
      "org.typelevel"      %% "jawn-parser"                   % "1.5.1",
      "io.altoo"           %% "akka-kryo-serialization-typed" % "2.5.1",
      "io.lemonlabs"       %% "scala-uri"                     % "4.0.3",
      "org.scalikejdbc"    %% "scalikejdbc"                   % versions.jdbc,
      "org.scalikejdbc"    %% "scalikejdbc-config"            % versions.jdbc,
      "org.scalactic"      %% "scalactic"                     % versions.scalaTest,
      "io.scalaland"       %% "chimney"                       % "0.7.5",
      "ch.megard"          %% "akka-http-cors"                % "1.2.0",
    ) ++ Seq(
      "com.datastax.oss"  % "java-driver-core"   % "4.17.0",
      "io.netty"          % "netty-handler"      % "4.1.97.Final",
      "org.owasp.encoder" % "encoder"            % "1.2.3",
      "org.yaml"          % "snakeyaml"          % "2.2",
      "ch.qos.logback"    % "logback-classic"    % "1.4.11",
      "com.h2database"    % "h2"                 % "2.2.224",
      "com.zaxxer"        % "HikariCP"           % "5.0.1",
      "io.sentry"         % "sentry"             % versions.sentry,
      "io.sentry"         % "sentry-logback"     % versions.sentry,
      "io.hypersistence"  % "hypersistence-tsid" % "2.1.1"
    ) ++ Seq(
      "io.kamon" %% "kamon-bundle"       % versions.kamon,
      "io.kamon" %% "kamon-apm-reporter" % versions.kamon,
    ),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies, inquireVersions,
      runClean, runTest,
      setReleaseVersion, commitReleaseVersion, tagRelease,
      setNextVersion, commitNextVersion, pushChanges
    ),
    jibBaseImage := "eclipse-temurin:17-jre-alpine",
    jibRegistry := "ghcr.io",
    jibCustomRepositoryPath := Some("jvorhauer/spikes"),
    jibTcpPorts := List(8080),
    jibUseCurrentTimestamp := true,
    jibName := "spikes",
    jibTags := List("latest"),
    jibTargetImageCredentialHelper := Some("docker-credential-osxkeychain"),
  )
  .enablePlugins(BuildInfoPlugin).settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, BuildInfoKey.action("buildTime") { java.time.LocalDateTime.now() }),
    buildInfoPackage := "spikes.build"
  )
