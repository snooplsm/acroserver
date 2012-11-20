scalaVersion := "2.9.2"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-netty-server" % "0.6.2" % "test",
  "io.netty" % "netty" % "3.4.4.Final"
)

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"
