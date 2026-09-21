FROM public.ecr.aws/docker/library/maven:3.9.9-eclipse-temurin-8 AS builder

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode -pl jd-dbms-admin -am -DskipTests package

FROM public.ecr.aws/docker/library/eclipse-temurin:8-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/jd-dbms-admin/target/jd-dbms-admin.jar /app/jddbms.jar
COPY --from=builder /workspace/assets/jar/ /app/assets/jar/
COPY docker/entrypoint.sh /app/entrypoint.sh

RUN useradd --system --uid 10001 --create-home --home-dir /app jddbms \
    && mkdir -p /data/jddbms/uploadUrl /home/ruoyi/logs \
    && chmod 0755 /app/entrypoint.sh \
    && chown -R jddbms:jddbms /app /data/jddbms /home/ruoyi

USER jddbms
EXPOSE 28080

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75.0" \
    DBMS_PROFILE=/data/jddbms \
    PIGENHOLE_UPLOADURL=/data/jddbms/uploadUrl \
    SPRING_DEVTOOLS_RESTART_ENABLED=false

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=8 \
    CMD curl --fail --silent http://127.0.0.1:28080/captchaImage > /dev/null || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
