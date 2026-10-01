# AS-IS runtime image for BuildYourHealth.
#
# There is no build tool (no Maven/Gradle), so the Java sources are compiled
# with the JDK that ships with the Tomcat image and the exploded webapp is
# assembled by hand as webapps/BuildYourHealth.
#
# Nothing under BuildYourHealth/ is modified; the sources are copied in and
# compiled as they are.
FROM tomcat:10.1-jdk17

ENV LANG=C.UTF-8 \
    CATALINA_HOME=/usr/local/tomcat \
    APP_DIR=/usr/local/tomcat/webapps/BuildYourHealth

# Only the AS-IS app is served; drop any default webapps that may be present.
RUN rm -rf "${CATALINA_HOME}/webapps"/*

# Exploded webapp exactly as it lives in the repository (JSPs, web.xml,
# WEB-INF/lib/*.jar).
COPY BuildYourHealth/src/main/webapp/ "${APP_DIR}/"

# Java sources and resource bundles.
COPY BuildYourHealth/src/main/java/ /tmp/asis-src/

# Compile against Tomcat 10.1's Servlet/JSP API jars plus the app's own jars,
# then place the bundle/*.properties on the classpath at
# WEB-INF/classes/bundle/ where <fmt:bundle basename="bundle.message"> looks.
RUN set -eux; \
    mkdir -p "${APP_DIR}/WEB-INF/classes/bundle"; \
    find /tmp/asis-src -name '*.java' > /tmp/asis-sources.txt; \
    javac -encoding UTF-8 \
        -cp "${CATALINA_HOME}/lib/servlet-api.jar:${CATALINA_HOME}/lib/jsp-api.jar:${APP_DIR}/WEB-INF/lib/*" \
        -d "${APP_DIR}/WEB-INF/classes" \
        @/tmp/asis-sources.txt; \
    cp /tmp/asis-src/bundle/*.properties "${APP_DIR}/WEB-INF/classes/bundle/"; \
    rm -rf /tmp/asis-src /tmp/asis-sources.txt
