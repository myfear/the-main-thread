# A standard OCI workload image for OpenShell 0.1.x.
FROM docker.io/library/node:24-bookworm-slim

ARG BOB_VERSION=2.0.8

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates curl git ripgrep \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global "@ibm/bob@${BOB_VERSION}" \
    && npm cache clean --force \
    && mkdir -p /sandbox \
    && chown node:node /sandbox

ENV HOME=/home/node
USER node
WORKDIR /sandbox

# For plain Podman smoke tests. OpenShell replaces CMD/ENTRYPOINT;
# pass `-- bob ...` explicitly when creating an OpenShell sandbox.
CMD ["bob", "--version"]
