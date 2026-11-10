# Rotate the JVM behind a Quarkus service

This companion serves `/runtime`, a JSON endpoint that reports the Java version and vendor of the running Quarkus process. Build the fast-jar once and use Brewlet to run it with a separately managed Java 21 runtime.

[Rotating the JVM Behind a Quarkus Service with Brewlet](https://www.the-main-thread.com/p/brewlet-quarkus)

## Build the application

Use JDK 25 to build the app. The Maven compiler targets Java 21 to match the two runtime images used in the lab. Run the Maven wrapper from this directory.

```sh
./mvnw package
```

The build runs the endpoint test and produces `target/quarkus-app/`. Keep that entire directory together.

## Build the Brewlet artifact

Use the verified [Brewlet 0.8.1 CLI](https://github.com/microsoft/brewlet/releases/tag/v0.8.1) and `jq`. The lab uses an ARM64 Linux node and a fresh staging directory for every build.

```sh
ARCH=arm64
WORK=$(mktemp -d)
APP_REPO=example.invalid/brewlet-quarkus/demo
APP_TAG="${APP_REPO}:lab"
mkdir "$WORK/payload"
cp -R target/quarkus-app/. "$WORK/payload/"
chmod -R a+rX "$WORK/payload"
brewlet build "$APP_TAG" \
  --platform "linux/$ARCH" \
  --launch launch.json \
  --layer "app=$WORK/payload:/app" \
  --store "$WORK/app-oci" \
  --result "$WORK/build.json"
APP_REF="$APP_REPO@$(jq -er .digest "$WORK/build.json")"
printf '%s\n' "$APP_REF"
```

`launch.json` calls `/usr/bin/java` in the runtime root and starts the fast-jar under `/app`. The OCI layout contains the application, its libraries, and the launch descriptor.

## Preview the payload with Podman

Start Podman. On macOS, Podman needs a running machine. These two pinned ARM64 distroless images contain Eclipse Temurin 21.0.12.1 and 21.0.13. They are inputs to the experiment. Save both references for the cluster steps.

```sh
RUNTIME_BEFORE=gcr.io/distroless/java21-debian13@sha256:5f4ce7e7d05ddf26f045fd1d8534f3c67d870fe5fe5204d8cf6940d3e440435f
RUNTIME_AFTER=gcr.io/distroless/java21-debian13@sha256:8afc7512ea432246246613fea8782ba1b55e772cdf8e4e9b8c8fa2a7d5535e77

BREWLET_CONTAINER_ENGINE=podman brewlet run "$APP_TAG" \
  --store "$WORK/app-oci" \
  --runtime "java-21=$RUNTIME_BEFORE" \
  --user 65532:65532 \
  -p 127.0.0.1:18080:8080
```

In a second terminal, run `curl -fsS http://127.0.0.1:18080/runtime`. Expect JSON containing `javaVersion`, `javaRuntimeVersion`, and `javaVendor`. Press Ctrl+C in the first terminal when you finish.

## Start the disposable kind cluster

Use kind 0.33.0, a running rootful Podman machine on macOS, `kubectl`, Helm, and the verified Brewlet 0.8.1 chart archive from the same release as the CLI. Keep several GiB free inside the Podman machine for the node image and both runtime generations. The following commands use Brewlet's pinned Kubernetes 1.35 node image with containerd 2.2 and config version 3. Brewlet modifies and restarts containerd inside this lab node. [Brewlet's node prerequisites](https://github.com/microsoft/brewlet/blob/v0.8.1/docs/installation.md)

Set `CHART` to the absolute path of the verified `brewlet-0.8.1.tgz`. Use the shell where you built the artifact and set the runtime image references.

```sh
CLUSTER=brewlet-rotation-kind
CTX="kind-$CLUSTER"
NODE="$CLUSTER-control-plane"
export KUBECONFIG="$WORK/kubeconfig"
export KIND_EXPERIMENTAL_PROVIDER=podman
kind create cluster --name "$CLUSTER" --kubeconfig "$KUBECONFIG" \
  --image kindest/node@sha256:4613778f3cfcd10e615029370f5786704559103cf27bef934597ba562b269661 \
  --wait 3m
k() { kubectl --context "$CTX" "$@"; }
helm show crds "$CHART" | k apply -f -
helm --kube-context "$CTX" upgrade --install brewlet "$CHART" \
  --namespace brewlet --create-namespace --wait --timeout 3m
k label "node/$NODE" "brewlet.example.com/pool=$CLUSTER"
k apply -f - <<EOF
apiVersion: node.brewlet.sh/v1alpha1
kind: RuntimeImage
metadata:
  name: java-21
spec:
  source: {image: "$RUNTIME_BEFORE"}
  user: {uid: 65532, gid: 65532}
---
apiVersion: node.brewlet.sh/v1alpha1
kind: NodeProfile
metadata:
  name: $CLUSTER
spec:
  nodePool:
    key: brewlet.example.com/pool
    names: [$CLUSTER]
    includeControlPlane: true
  runtimeImages:
    - {id: java-21, install: Eager}
  rollout: {maxUnavailable: 1, containerdRestart: validated}
EOF
k wait --for=condition=Ready "nodeprofile/$CLUSTER" --timeout=3m
```

The profile opts in to provisioning the control-plane node because this cluster has one node. To deploy without an application registry, import the local OCI layout and tag it with the exact reference the Deployment uses.

```sh
tar -C "$WORK/app-oci" -cf "$WORK/app.tar" oci-layout index.json blobs
podman cp "$WORK/app.tar" "${NODE}:/tmp/brewlet-quarkus-app.tar"
podman exec "$NODE" ctr -n k8s.io images import --all-platforms /tmp/brewlet-quarkus-app.tar
podman exec "$NODE" ctr -n k8s.io images tag "$APP_TAG" "$APP_REF"
k create namespace brewlet-demo
k set image --local -f k8s/deployment.yaml "app=$APP_REF" -o yaml \
  | k -n brewlet-demo apply -f -
k -n brewlet-demo rollout status deployment/brewlet-quarkus --timeout=2m
```

The manifest sets `imagePullPolicy: Never` for the preloaded artifact. Keep the lab to one node with the architecture selected at build time. The manifest also mounts an `emptyDir` at `/app/cache` for the Vert.x file cache. The runtime root and packaged application files remain read-only. [Quarkus Vert.x configuration](https://quarkus.io/version/3.40/guides/all-config/#quarkus-vertx_quarkus-vertx-cache-directory)

To call the endpoint, run `k -n brewlet-demo port-forward deployment/brewlet-quarkus 18080:8080`. In a second terminal, call `curl -fsS http://127.0.0.1:18080/runtime`. Stop port forwarding before replacing the Pod.

## Verify rotation

Use the commands below to inspect the current running Pod. The node's binding observation updates asynchronously. If the binding is missing, wait a few seconds and repeat the query.

```sh
POD=$(k -n brewlet-demo get pods -l app=brewlet-quarkus -o json \
  | jq -er '.items[] | select(.metadata.deletionTimestamp == null and any(.status.containerStatuses[]?; .ready)) | .metadata.name')
POD_UID=$(k -n brewlet-demo get pod "$POD" -o jsonpath='{.metadata.uid}')
k -n brewlet-demo get pod "$POD" -o jsonpath='{.status.containerStatuses[0].imageID}{"\n"}'
k get node "$NODE" -o json \
  | jq -e --arg uid "$POD_UID" '
      .metadata.annotations["brewlet.sh/runtime-bindings"] | fromjson
      | select(.complete) | .bindings[]
      | select(.podUID == $uid and .id == "java-21" and .state == "running")
      | {containerID, digest, generation}'
k get --raw "/api/v1/namespaces/brewlet-demo/pods/${POD}:8080/proxy/runtime"
```

1. Record the application image ID, the Pod UID, its Brewlet runtime binding, and the `/runtime` response.
2. Run `brewlet k8s runtime rotate java-21 --context "$CTX" --image "$RUNTIME_AFTER" --yes`. Wait for the inventory to report that digest and `RotationComplete` for its current resource generation. Read the original Pod's binding and call `/runtime` again to verify that it still uses the first runtime.
3. Run `k -n brewlet-demo rollout restart deployment/brewlet-quarkus` and wait for rollout status. Run the inspection commands again. Verify that the replacement reports Java 21.0.13 and a new runtime digest, and that its application image ID still equals `APP_REF`.

See [Brewlet's runtime binding and rotation reference](https://github.com/microsoft/brewlet/blob/v0.8.1/docs/operations.md#observe-actual-bindings). To finish, delete the lab namespace, follow [Brewlet's removal procedure](https://github.com/microsoft/brewlet/blob/v0.8.1/docs/uninstallation.md), then run `kind delete cluster --name "$CLUSTER" --kubeconfig "$KUBECONFIG"`.
