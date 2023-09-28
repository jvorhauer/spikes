resolvers += "Artima Maven Repository" at "https://repo.artima.com/releases"

addSbtPlugin("com.github.sbt"         % "sbt-release"            % "1.1.0")
addSbtPlugin("ch.epfl.scala"          % "sbt-version-policy"     % "2.1.0")
addSbtPlugin("com.timushev.sbt"       % "sbt-updates"            % "0.6.4")
addSbtPlugin("ch.epfl.scala"          % "sbt-scalafix"           % "0.11.1")
addSbtPlugin("de.gccc.sbt"            % "sbt-jib"                % "1.3.6")
addSbtPlugin("org.scoverage"          % "sbt-scoverage"          % "2.0.9")
addSbtPlugin("org.scalameta"          % "sbt-scalafmt"           % "2.5.2")
addSbtPlugin("com.eed3si9n"           % "sbt-buildinfo"          % "0.11.0")
addSbtPlugin("com.artima.supersafe"   % "sbtplugin"              % "1.1.12")
addSbtPlugin("com.sksamuel.scapegoat" % "sbt-scapegoat"          % "1.2.2")
addDependencyTreePlugin
