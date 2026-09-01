# Parallel Bob Worktree Lab

This Quarkus project accompanies the Main Thread tutorial about running two Bob Shell 2.x coding tasks in Git worktrees inside one Podman container.

The starting commit contains two independent REST endpoints and their baseline tests. The files under `prompts/` ask Bob to add catalog search in one worktree and express shipping in another. The scripts keep the host API key outside the repository, mount the repository at the same absolute path inside the container so linked-worktree metadata remains valid, and privately relabel writable mounts so named volumes remain usable after the container is replaced on an SELinux-enforcing Podman machine.

See the parent [`article.md`](../article.md) for the complete walkthrough, limitations, and verification steps.
