import ReleaseTransformations._

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

lazy val AkkaVersion = "2.7.0"
lazy val AkkaHttpVersion = "10.4.0"
lazy val ScalaTestVersion = "3.2.14"
lazy val Test = "test"

lazy val root = (project in file("."))
  .settings(
    name := "spikes",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
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
      "com.typesafe.akka"  %% "akka-actor-typed"             % AkkaVersion,
      "com.typesafe.akka"  %% "akka-stream"                  % AkkaVersion,
      "com.typesafe.akka"  %% "akka-http"                    % AkkaHttpVersion,
      "com.typesafe"       %% "ssl-config-core"              % "0.6.1",
      "com.typesafe.akka"  %% "akka-persistence-typed"       % AkkaVersion,
      "com.typesafe.akka"  %% "akka-serialization-jackson"   % AkkaVersion,
      "com.typesafe.akka"  %% "akka-persistence-cassandra"   % "1.1.0",
      "de.heikoseeberger"  %% "akka-http-circe"              % "1.39.2",
    ) ++ Seq(
      "org.scalatest"     %% "scalatest"                % ScalaTestVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion,
      "net.datafaker"     %  "datafaker"                % "1.7.0",
    ).map(_ % Test) ++ Seq(
      "io.scalaland"       %% "chimney"                   % "0.6.2",
      "ch.qos.logback"     %  "logback-classic"           % "1.4.5",
      "io.circe"           %% "circe-generic"             % "0.14.3",
      "org.scalactic"      %% "scalactic"                 % ScalaTestVersion,
      "org.wvlet.airframe" %% "airframe-ulid"             % "22.12.6",
      "fr.davit"           %% "akka-http-metrics-datadog" % "1.7.1",
    ),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
  )
