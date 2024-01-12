set JAVA_HOME=D:\dev\pub\java\jdk-18.0.2.1
set M3_HOME=D:\dev\pub\maven\apache-maven-3.8.4
set PATH=%JAVA_HOME%\bin;%M3_HOME%\bin
rem mvn clean install -Drevision=1.0.0-SNAPSHOT

call java -cp maven.rev.set.jar maven.rev.set.PomReviser . mvn.cmd clean install