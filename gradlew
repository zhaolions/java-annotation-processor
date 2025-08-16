#!/usr/bin/env sh

# Gradle start up script for UN*X

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Resolve application home directory
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(. *\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
SAVED="`pwd`"
cd "`dirname "$PRG"`/.." >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

DEFAULT_JVM_OPTS=""

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
fi

# For Cygwin or MSYS, ensure paths are in UNIX format before anything is touched.
cygwin=false
darwin=false
msys=false
case "`uname`" in
  CYGWIN* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MINGW* ) msys=true ;;
  MSYS* ) msys=true ;;
  *) ;;
esac

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if $cygwin ; then
  APP_HOME=`cygpath --path --mixed "$APP_HOME"`
  CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
  JAVACMD=`cygpath --unix "$JAVACMD"`
fi

# Increase the maximum file descriptors if we can.
if [ "$darwin" = "false" ]; then
  MAX_FD=maximum
  if [ -n "$MAX_FD" ]; then
    ulimit -n $MAX_FD > /dev/null 2>&1 || true
  fi
fi

# Collect all arguments for the java command
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
