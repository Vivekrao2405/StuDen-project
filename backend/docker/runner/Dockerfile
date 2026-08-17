# Phase 7.5 combined code-execution sandbox image: Java, Python, C, C++ in one image so
# DockerCodeExecutionService only ever has to pull/manage a single runner image. Built explicitly
# (not multi-stage) -- there's nothing to discard, every installed toolchain is needed at runtime.
#
# Build:  docker build -t studen-code-runner:latest backend/docker/runner
FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 gcc g++ \
    && rm -rf /var/lib/apt/lists/*

# Non-root, no login shell, no home-dir writes needed -- the only writable paths at container-run
# time are the two bind mounts (/workspace, /run) supplied by DockerCodeExecutionService, plus the
# tmpfs mounted at /tmp for compiler scratch space.
RUN useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin sandbox

COPY run.sh /opt/runner/run.sh
RUN chmod +x /opt/runner/run.sh && chown -R sandbox:sandbox /opt/runner

USER sandbox
WORKDIR /workspace

ENTRYPOINT ["/bin/sh", "/opt/runner/run.sh"]
