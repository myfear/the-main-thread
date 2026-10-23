# Run IBM Bobshell inside NVIDIA OpenShell

Run Bob Shell 2.0.8 in a Podman image, attach an OpenShell 0.1.5 credential
provider, and let Bob repair a retry function. Verify the repaired code and
check which filesystem paths and network destinations the policy denies.

Companion to [Run IBM Bobshell inside NVIDIA OpenShell](https://www.the-main-thread.com/p/ibm-bobshell-nvidia-openshell-sandbox).

## Prerequisites

- Podman and an authenticated OpenShell 0.1.5 gateway using the Podman driver.
  The gateway must use the same image store as `podman build`.
- A Bob account and an **Inference** API key. See IBM's
  [API-key instructions](https://bob.ibm.com/docs/shell/account/api-keys).
  The authenticated task consumes account credits.
- Bash for the provided scripts. Node 24 and Bob are installed inside the image;
  neither is required on the host.

The tested environment is Apple Silicon, Podman 6.1.3, OpenShell 0.1.5, and
Node 24.21.0 inside a Linux ARM64 container. Follow [the isolated gateway setup](GATEWAY.md)
if you do not have a gateway. Run the commands below from this directory in the
terminal configured by that setup.

## Build the image

```sh
podman build --pull=missing -t localhost/bobshell-openshell:2.0.8 .
podman run --rm --network=none localhost/bobshell-openshell:2.0.8
```

Expect Bob `2.0.8`, commit `813289c5`. The image pins Bob; its Node base tag and
Debian packages can change. The `.containerignore` excludes local state and
all other files from the build context except `Dockerfile`.

## Register the provider

```sh
openshell profile lint -f bob-provider.yaml
openshell profile import -f bob-provider.yaml
bash scripts/register-provider.sh
```

Paste the Inference API key at the hidden prompt. The script passes it through
its child-process environment and exits after creating `bob-test`. It does not
put the key in shell history, process arguments, or a project file. OpenShell
stores the credential in gateway state. The lab profile authorizes the US East
API endpoint for `/usr/local/bin/node`.

## Create and inspect the sandbox

```sh
openshell sandbox create \
  --name bob-auth \
  --from localhost/bobshell-openshell:2.0.8 \
  --policy policy-deny-all.yaml \
  --provider bob-test \
  --detach -- sleep infinity
openshell policy get bob-auth --base
openshell policy get bob-auth --full
openshell sandbox exec -n bob-auth --no-login-shell -- node -e \
  'console.log((process.env.BOB_API_KEY || "").startsWith("openshell:resolve:env:"))'
```

The base policy has no outbound destinations. The full policy adds the
provider's IBM endpoint for Node. The last command must print `true`, indicating
that the environment contains an OpenShell placeholder.

## Run the repair

Upload the intentionally broken starting code and run its tests:

```sh
openshell sandbox upload bob-auth fixture /sandbox --no-git-ignore
openshell sandbox exec -n bob-auth --no-login-shell \
  --workdir /sandbox/fixture -- node --test retry-delay.test.mjs
```

Expect **2 passes, 2 failures**, and a nonzero exit. Run this baseline separately
from a shell script using `set -e`.

Review the license with
`podman run --rm --network=none localhost/bobshell-openshell:2.0.8 bob --show-license`.
After accepting it, run:

```sh
openshell sandbox exec -n bob-auth --timeout 120 --no-login-shell \
  --workdir /sandbox/fixture -- \
  bob run --accept-license --trust --disable-mcp --disable-subagents \
    --max-cost 0.50 --max-turns 8 --format json \
    'Read README.md and fix retry-delay.mjs so node --test retry-delay.test.mjs passes. Do not edit the tests or install dependencies. Run the tests and report the result.'
```

`--trust` applies to the sample you supplied. The prompt's instruction to keep
tests unchanged is checked afterward; the filesystem policy permits edits to
the entire sample directory.

## Verify the result independently

```sh
openshell sandbox exec -n bob-auth --no-login-shell \
  --workdir /sandbox/fixture -- node --test retry-delay.test.mjs
mkdir -p result
openshell sandbox download bob-auth /sandbox/fixture result
cmp fixture/retry-delay.test.mjs result/retry-delay.test.mjs
diff -u fixture/retry-delay.mjs result/retry-delay.mjs
podman run --rm --network=none \
  -v "$PWD/result:/sandbox/fixture:ro" -w /sandbox/fixture \
  localhost/bobshell-openshell:2.0.8 node --test retry-delay.test.mjs
```

Expect **4 passes, 0 failures**. `cmp` must exit 0 with no output. `diff` normally
exits 1 because the implementation changed. Inspect its output before accepting
the change. The final test uses the original image with no network and a
read-only mount of the downloaded result. `completed/` contains the original
experiment's repair for comparison; your agent response can differ.

## Check denied access

Run these commands separately; two deliberately exit nonzero:

```sh
openshell sandbox exec -n bob-auth --no-login-shell -- node -e \
  'fetch("https://example.com",{signal:AbortSignal.timeout(5000)}).then(()=>{console.error("UNEXPECTED_NETWORK_ACCESS");process.exit(1)}).catch(()=>console.log("request-failed-check-audit-log"))'
openshell sandbox exec -n bob-auth --no-login-shell -- \
  curl -sS --max-time 8 https://api.us-east.bob.ibm.com
openshell sandbox exec -n bob-auth --no-login-shell -- ls /opt
openshell logs bob-auth -n 500
```

The original run returned a fetch failure, curl exit 7, and `ls` exit 2 with
permission denied. Require audit events naming Node → `example.com:443` and
curl → `api.us-east.bob.ibm.com:443` as **DENIED**. A timeout alone is not proof
of policy enforcement.

For controls using the same image without OpenShell:

```sh
podman run --rm localhost/bobshell-openshell:2.0.8 node -e \
  'fetch("https://example.com",{signal:AbortSignal.timeout(10000)}).then(r=>console.log(r.status)).catch(e=>{console.error(e.message);process.exit(1)})'
podman run --rm --network=none localhost/bobshell-openshell:2.0.8 ls /opt
```

The original controls returned HTTP 200 and a readable `/opt` directory.

## Scope and troubleshooting

The custom profile covers the tested API-key flow through US East. Consult
[IBM's firewall guidance](https://bob.ibm.com/docs/ide/troubleshooting/ts-firewall-rules)
for other regions, browser SSO, or additional services. If inference fails,
inspect the audit log before adding destinations. A permitted request returning
an authentication error requires checking the key, its type, and account access.

The profile permits any JavaScript using `/usr/local/bin/node` to call the
approved host and does not restrict individual API paths. It does not prevent
all possible use of the attached credential. The sandbox can modify all files
under `/sandbox`; inspect the downloaded diff and compare the tests.

Office-document tools, interactive sessions, MCP, subagents, other regions,
and x86_64 were not exercised by the original experiment.

## Cleanup

After downloading any result you want to keep:

```sh
openshell sandbox delete bob-auth
openshell sandbox list
```

Deletion is asynchronous. Wait until `bob-auth` disappears from the list before
removing the provider; a sandbox in `Deleting` can retain its attachment briefly.
Then run:

```sh
openshell provider delete bob-test
openshell profile delete ibm-bob
```

If you started the isolated gateway, follow [its shutdown steps](GATEWAY.md#stop-and-remove-the-lab-state)
to stop it and remove credential storage and certificates. Revoke a disposable
key in your Bob account. Optionally remove the image with
`podman rmi localhost/bobshell-openshell:2.0.8`.
