@echo off

set DEFAULT_JAVASVN_HOME=%~dp0

if "%JAVASVN_HOME%"=="" set JAVASVN_HOME=%DEFAULT_JAVASVN_HOME%

set JAVASVN_CLASSPATH= "%JAVASVN_HOME%javasvn.jar";"%JAVASVN_HOME%javasvn-cli.jar";"%JAVASVN_HOME%jsch.jar"
set JAVASVN_MAINCLASS=org.tmatesoft.svn.cli.SVN
REM set JAVASVN_OPTIONS=-Djavasvn.log.svn=true

"%JAVA_HOME%\bin\java" %JAVASVN_OPTIONS% -cp %JAVASVN_CLASSPATH% %JAVASVN_MAINCLASS% %*