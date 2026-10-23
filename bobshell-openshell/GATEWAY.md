# Start an isolated OpenShell gateway on macOS

This setup was verified on Apple Silicon with Podman 6.1.3 and OpenShell 0.1.5.
It runs a foreground gateway on `https://127.0.0.1:18771`. Its certificates,
database, and CLI registration stay under this project's ignored `.local/`
directory. The gateway uses your existing Podman image store.

## Prepare Podman and OpenShell

Install [Podman](https://podman.io/docs/installation), then run `podman info`.
If no machine exists, run `podman machine init`. Start a stopped machine with
`podman machine start`, then confirm `podman info` succeeds. This example uses
the machine's default networking; custom host-loopback addresses require matching
gateway configuration and certificate names.

Run these commands from this project's root. Download the CLI and gateway from
the [OpenShell 0.1.5 release](https://github.com/NVIDIA/OpenShell/releases/tag/v0.1.5):

```sh
mkdir -p .local/bin
curl -fLsS https://github.com/NVIDIA/OpenShell/releases/download/v0.1.5/openshell-aarch64-apple-darwin.tar.gz \
  -o .local/openshell.tar.gz
curl -fLsS https://github.com/NVIDIA/OpenShell/releases/download/v0.1.5/openshell-gateway-aarch64-apple-darwin.tar.gz \
  -o .local/gateway.tar.gz
tar -xzf .local/openshell.tar.gz -C .local/bin
tar -xzf .local/gateway.tar.gz -C .local/bin
export PATH="$PWD/.local/bin:$PATH"
openshell --version
openshell-gateway --version
```

Both versions must be `0.1.5`. These archives are for Apple Silicon. For other
platforms, use the matching release artifacts and the official
[installation guide](https://docs.nvidia.com/openshell/v0.1.5/about/installation).
The complete lab has not been validated on those platforms.

## Start the gateway

In the same terminal:

```sh
bash scripts/start-gateway.sh
```

Keep this terminal open. The script creates a local certificate authority,
server and client certificates, and signing keys. It writes a configuration
with `compute_driver = "podman"`, TLS enabled, and mTLS user authentication.
It validates that configuration before launching the gateway. Inspect the
generated `.local/config/gateway.toml` if startup fails.

The script includes certificate names for localhost and the default Podman
Machine host callback. The container supervisor uses the gateway's CA to
verify that callback. The local TLS files stay on the host; the script does
not mount the host home directory into the workload.

## Register the CLI

Open a second terminal in the same project root:

```sh
chmod +x scripts/openshell
export PATH="$PWD/scripts:$PWD/.local/bin:$PATH"
openshell gateway add https://127.0.0.1:18771 --local --name bob-tutorial
openshell status
```

The status must report `Connected`, `Authenticated (mTLS transport)`, and
version `0.1.5`. Continue with the [lab instructions](README.md#build-the-image)
in this second terminal. The `scripts/openshell` wrapper applies the isolated
configuration only to OpenShell, preserving Podman's saved connection settings.
A new terminal needs the same `PATH` export; registration is needed only once
per setup. Keep the project's directory in `PATH` only for this lab session.

## Troubleshoot startup

- **No responsive Podman socket:** confirm `podman info` succeeds in the terminal
  starting the gateway. The gateway and CLI must use the same Podman machine.
- **Address already in use:** another process owns port 18771. Stop an earlier
  instance of this lab, or change the port in the script and registration URL.
- **TLS callback failure:** confirm that the machine uses its default host
  networking. See NVIDIA's [Podman driver configuration](https://docs.nvidia.com/openshell/v0.1.5/how-it-works/sandboxes/runtimes#podman-driver)
  for custom addresses. Keep certificate verification enabled.
- **Landlock qualification fails:** use a Linux guest supporting the required
  enforcement. The lab deliberately requires Landlock rather than silently
  dropping filesystem restrictions.

## Stop and remove the lab state

First delete the sandbox, provider, and profile using the cleanup commands in
the README. Then press **Ctrl+C** in the gateway terminal. From the project root,
remove this lab's private database and certificates:

```sh
rm -rf .local/config .local/state
```

This deletes only the isolated state created here. It does not revoke the Bob
API key at IBM; revoke a disposable key in your account when you finish.
