# Workshop model registry on a Raspberry Pi

Preload Qwen3 1.7B on a Raspberry Pi, distribute it over a workshop LAN, and run a small Quarkus/LangChain4j code-review application on each participant's laptop. The Pi serves model files and caches Maven Central artifacts; laptop Ollama performs inference.

Companion to [Ollama for Workshops: A Model Registry on a Raspberry Pi](https://www.the-main-thread.com/p/ollama-workshop-registry-raspberry-pi).

## Requirements

**Registry host:** a 64-bit Linux machine with systemd, Podman 5.4.2 or newer, SSH, sudo access, and an SSD already mounted at `/mnt/ssd`. The example uses a Raspberry Pi 4 with 8 GB RAM and wired gigabit Ethernet. Ports 5001, 5002, and 5003 must be free. Leave space for the 10 GB Maven cache quota plus model files, images, and temporary uploads. No Ollama daemon or Compose provider is needed on the Pi.

**Organizer laptop:** Ollama 0.40.13 or newer, Bash, curl, jq, and SSH. Start Ollama before preloading. Java 25 is also required to prepare the Maven cache. Internet access is needed while fetching the model, container images, and Maven artifacts.

**Participant laptop:** local Ollama 0.40.13 or newer, Java 25, and the supplied Maven wrapper. Bash setup also needs curl and jq; native Windows has a PowerShell script. The native Windows flow was not exercised on a Windows host for this companion. Model weights are about 1.4 GB; inference requires additional RAM and its speed depends on the laptop. This is not a demonstrated minimum-hardware specification.

Use a LAN on which participants can reach the Pi. Guest Wi-Fi may isolate clients. This setup uses unauthenticated HTTP on the workshop LAN; use it on a trusted network without internet port forwarding. Digest checking identifies the organizer's selected content but does not encrypt downloads. The upload port is reachable only through localhost or SSH.

## Install the registry

Run laptop commands from this directory. Replace the example host:

```bash
export PI_SSH=pi@192.168.1.20
ssh "$PI_SSH" 'mkdir -p ~/ollama-workshop-registry/quadlet'
scp nginx.conf reposilite.json "$PI_SSH:ollama-workshop-registry/"
scp quadlet/ollama-workshop.pod \
    quadlet/ollama-workshop-registry.container \
    quadlet/ollama-workshop-downloads.container \
    quadlet/ollama-workshop-maven.container \
    "$PI_SSH:ollama-workshop-registry/quadlet/"
ssh "$PI_SSH"
```

On the Pi:

```bash
cd ~/ollama-workshop-registry
mountpoint /mnt/ssd
sudo install -d /mnt/ssd/ollama-workshop-registry/data \
  /mnt/ssd/ollama-workshop-registry/maven-data /etc/containers/systemd
sudo install -m 0644 nginx.conf reposilite.json /mnt/ssd/ollama-workshop-registry/
sudo podman pull docker.io/library/registry:3.1.2
sudo podman pull docker.io/library/nginx:1.30.6-alpine
sudo podman pull docker.io/dzikoysk/reposilite:3.6.6
sudo install -m 0644 quadlet/ollama-workshop.pod \
    quadlet/ollama-workshop-registry.container \
    quadlet/ollama-workshop-downloads.container \
    quadlet/ollama-workshop-maven.container \
    /etc/containers/systemd/
sudo systemctl daemon-reload
sudo systemctl start ollama-workshop-pod.service
sudo systemctl start ollama-workshop-downloads.service
curl -fsS http://127.0.0.1:5002/v2/
```

The final command returns `{}`. Starting the downloads service explicitly waits for the three workload containers' health checks. Podman also creates its small infrastructure container; first startup must happen before the event while internet is available. On the verified Pi, Podman generated its local pause image automatically.

The Quadlet pod publishes `127.0.0.1:5001` for uploads, `0.0.0.0:5002` for model downloads, and `0.0.0.0:5003` for Maven downloads. Its `[Install]` section arranges startup at boot; do not run `systemctl enable` on generated units. SSD mount ordering and a mount-point assertion prevent startup against an unmounted `/mnt/ssd`. If you use another storage mount, update the container volume paths, `RequiresMountsFor`, and `AssertPathIsMountPoint` before installation.

The three containers share a network namespace, so Nginx reaches Distribution at `127.0.0.1:5000` and Reposilite at `127.0.0.1:8081`. Nginx permits only GET and HEAD under `/v2/`, supports byte ranges, and retains the `Location` header needed by the verified Ollama client. Distribution retains absolute upload locations. Keep both compatibility settings. The Maven listener exposes only GET and HEAD under `/central/`; the Reposilite dashboard and API are not exposed to the LAN.

## Preload the model

Back on the organizer laptop, open a tunnel in a separate terminal:

```bash
ssh -N -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:15001:127.0.0.1:5001 "$PI_SSH"
```

With local Ollama running, use another terminal in this directory:

```bash
bash scripts/preload.sh --registry http://127.0.0.1:15001
```

`models.tsv` maps the upstream `qwen3:1.7b` to the local alias `workshop-qwen:1.7b`. The script pulls from upstream, copies the model to `127.0.0.1:15001/workshop/workshop-qwen:1.7b`, pushes to the Pi, checks the manifest digest, and writes `models.lock.json`.

The included lock file records the model used for this companion. Your preload regenerates it from the content you actually upload. Freeze the registry content and distribute the matching lock file before the workshop. Upstream tags can change.

To add another model, edit the two-column model list and repeat preload. `--reuse-local` uses an already-downloaded upstream model without refreshing its tag. The script requires a loopback Ollama daemon; `--registry` selects the registry endpoint separately. Close the SSH tunnel with Ctrl+C when preloading finishes.

## Prepare the Maven cache

Reposilite 3.6.6 stores Maven Central downloads in `/mnt/ssd/ollama-workshop-registry/maven-data`. `reposilite.json` enables storage with a 10 GB quota and prioritizes cached files. Cache misses require internet access from the Pi.

From `app/` on the organizer laptop, use fresh local directories so your existing Maven repository cannot hide downloads needed by participants:

```bash
export WORKSHOP_MAVEN_URL=http://192.168.1.20:5003/central
export MVNW_REPOURL="$WORKSHOP_MAVEN_URL"
export MAVEN_USER_HOME="$PWD/../.local/maven-preload-home"
./mvnw -B -ntp -s settings-workshop.xml \
  -Dmaven.repo.local="$PWD/../.local/maven-preload" \
  quarkus:go-offline package
unset MAVEN_USER_HOME
```

Wait for `BUILD SUCCESS`. `MVNW_REPOURL` sends the Maven distribution ZIP through the cache; the wrapper retains its SHA-256 check. `settings-workshop.xml` sends dependency and plugin resolution through the cache. The Quarkus goal prepares dependencies for development as well as packaging.

Repeat preparation for each participant operating system and architecture so platform-specific artifacts are cached. This companion was built through the Pi from macOS ARM64. Keep the project and dependency versions fixed after preparation. The mirror covers all Maven repository IDs (`mirrorOf=*`); this companion uses artifacts from Central. Configure additional upstreams in Reposilite before adapting the setup to a project that uses other repositories.

Pass the supplied settings file explicitly for each workshop Maven command. This leaves the user's `~/.m2/settings.xml` untouched. Maven's `-o` flag prevents downloads from the Pi too; use it only after dependencies have reached the laptop's own cache.

## Prepare participant files

Give participants the companion directory and its matching lock file. To create a smaller bundle with only setup and Java files:

```bash
tar -czf workshop-participant.tgz \
  models.lock.json scripts/common.sh scripts/setup-participant.sh \
  scripts/setup-participant.ps1 app/pom.xml app/mvnw app/mvnw.cmd \
  app/.mvn app/src app/review-request.json app/settings-workshop.xml \
  app/README.md PARTICIPANTS.md
```

Have participants install Java and Ollama and extract the bundle before the event. Supply both LAN endpoints: port 5002 for models and `http://PI_ADDRESS:5003/central` for Maven artifacts. Prepare the Pi cache before participants build the application.

## Use the model locally

Follow [PARTICIPANTS.md](PARTICIPANTS.md). The short macOS/Linux path is:

```bash
bash scripts/setup-participant.sh --registry http://192.168.1.20:5002
ollama show workshop-qwen:1.7b
cd app
export WORKSHOP_MAVEN_URL=http://192.168.1.20:5003/central
export MVNW_REPOURL="$WORKSHOP_MAVEN_URL"
./mvnw -s settings-workshop.xml quarkus:dev
```

In another terminal, from `app/`:

```bash
curl -fsS http://127.0.0.1:8080/review \
  -H 'Content-Type: application/json' \
  --data-binary @review-request.json
```

The response has an `answer` field. The supplied example asks about an out-of-bounds loop in a Java method. The application gives advice; it cannot execute or edit code. See [the application guide](app/README.md) for configuration and error responses.

Do not set `OLLAMA_HOST` to the Pi. That variable selects an inference daemon. Registry download addresses and local inference addresses serve different purposes.

## Operate the registry

On the Pi:

```bash
sudo systemctl status ollama-workshop-pod.service \
  ollama-workshop-registry.service ollama-workshop-maven.service \
  ollama-workshop-downloads.service
sudo podman ps --filter pod=ollama-workshop
sudo journalctl -u ollama-workshop-downloads.service -n 30 --no-pager
sudo journalctl -u ollama-workshop-maven.service -n 30 --no-pager
sudo systemctl restart ollama-workshop-pod.service
sudo systemctl start ollama-workshop-downloads.service
```

A restart recreates the pod and workload containers while preserving SSD model files and Maven artifacts. Manage them through systemd, including when using Cockpit. Avoid manually deleting individual service-managed containers.

For a 20–30-person event, connect the Pi by Ethernet and start downloads in small groups. Every laptop still receives its own model copy over the LAN. This setup has been exercised with one participant, not load-tested with a classroom.

If setup cannot reach the registry, check `/v2/` with curl, the Pi address, LAN routing, and client isolation. If a tag digest differs, distribute the matching lock file rather than disabling verification. Existing conflicting local aliases are preserved; only use `--replace-existing` or PowerShell `-ReplaceExisting` when replacement is intentional.

For Maven resolution failures, confirm `WORKSHOP_MAVEN_URL` and the `-s settings-workshop.xml` argument, then inspect the Maven service log. Populate missing artifacts while the Pi can reach Central, then retry with `-U` to clear Maven's cached failure.

## Stop or remove the registry

Stopping retains the installed units, model files, and Maven cache:

```bash
sudo systemctl stop ollama-workshop-pod.service
```

On Podman 5.4.2, the registry and Maven services can show failed-state markers after a requested shutdown (exit codes 2 and 143 respectively). Inspect their journal and confirm `sudo podman ps --filter pod=ollama-workshop` shows no running workshop containers. Then clear those markers with `sudo systemctl reset-failed ollama-workshop-registry.service ollama-workshop-maven.service`. This does not start the services.

For removal, stop it first, then remove only these workshop units:

```bash
sudo rm /etc/containers/systemd/ollama-workshop.pod \
  /etc/containers/systemd/ollama-workshop-registry.container \
  /etc/containers/systemd/ollama-workshop-downloads.container \
  /etc/containers/systemd/ollama-workshop-maven.container
sudo systemctl daemon-reload
```

This leaves the `data` and `maven-data` directories under `/mnt/ssd/ollama-workshop-registry/` intact. Delete either directory separately only when you intend to discard its contents. No other Pi containers or services need to be stopped.
