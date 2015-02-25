name := """DBpediaLiveChangesets"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  javaWs,
  "commons-io" % "commons-io" % "2.4",
  "com.google.guava" % "guava" % "14.0.1",
  "org.aksw.jena-sparql-api" % "jena-sparql-api-core" % "2.10.0-22"
)

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
