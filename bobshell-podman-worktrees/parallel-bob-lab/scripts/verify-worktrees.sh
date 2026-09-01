#!/usr/bin/env bash

set -euo pipefail

container_name="${BOB_CONTAINER_NAME:-bob-worktree-lab}"
repository_root="$(git rev-parse --show-toplevel)"
repository_root="$(cd "${repository_root}" && pwd -P)"
catalog_worktree="${repository_root}/.worktrees/catalog-search"
shipping_worktree="${repository_root}/.worktrees/express-shipping"

catalog_expected="src/main/java/com/mainthread/catalog/CatalogResource.java
src/test/java/com/mainthread/catalog/CatalogResourceTest.java"
shipping_expected="src/main/java/com/mainthread/shipping/ShippingQuoteResource.java
src/test/java/com/mainthread/shipping/ShippingQuoteResourceTest.java"

catalog_actual="$(git -C "${catalog_worktree}" diff --name-only | sort)"
shipping_actual="$(git -C "${shipping_worktree}" diff --name-only | sort)"

if [[ "${catalog_actual}" != "${catalog_expected}" ]]; then
    echo "Unexpected catalog worktree changes:" >&2
    echo "${catalog_actual}" >&2
    exit 1
fi

if [[ "${shipping_actual}" != "${shipping_expected}" ]]; then
    echo "Unexpected shipping worktree changes:" >&2
    echo "${shipping_actual}" >&2
    exit 1
fi

git -C "${catalog_worktree}" diff --check
git -C "${shipping_worktree}" diff --check

podman exec \
    --workdir "${catalog_worktree}" \
    --env QUARKUS_HTTP_TEST_PORT=8081 \
    "${container_name}" \
    ./mvnw --batch-mode --no-transfer-progress test

podman exec \
    --workdir "${shipping_worktree}" \
    --env QUARKUS_HTTP_TEST_PORT=8181 \
    "${container_name}" \
    ./mvnw --batch-mode --no-transfer-progress test

echo "Verified both worktrees and their expected file boundaries."
