# IBM Bob in disposable ISX workstations

Run two IBM Bob coding tasks in separate Linux workstations, then fetch their commits into a local Git checkout. ISX copies the same Quarkus application into each workstation and supplies Bob authentication through a host proxy.

Read the tutorial: [IBM Bob with ISX: Disposable Linux Workstations](https://www.the-main-thread.com/p/ibm-bob-isx-workstations).

The starting application uses Java 21 and Quarkus 3.39.1. `GET /catalog` returns three products, and `GET /shipping/quote` returns a standard quote priced at 12. The supplied prompts ask Bob to add catalog search and express shipping. Those features are tasks for the agents; they are not implemented in the starting application.

## Prerequisites

For the workstation workflow, use an Apple Silicon Mac with macOS 15 or later, Homebrew, Git, and an IBM Bob inference API key. Configure your Git commit identity before cherry-picking changes. Review and accept IBM's applicable license terms when initializing Bob.

To build and test the application directly on your host, install JDK 21. The Maven wrapper downloads Maven with a pinned checksum. The template supplies Java, Maven, Bob, and Podman inside the guest.

The template follows the tutorial's ISX 0.3.10 and Bob Shell 2.0.5 setup. It pins Temurin 21.0.12.1+1 for the Linux guest. Keep Java 21 when following these steps so the build target and guest tools match.

## Build and run the starting application

From the companion directory, build the application and run both the application tests and packaged JAR tests:

```bash
cd lab
./mvnw -B verify -DskipITs=false
java -jar target/quarkus-app/quarkus-run.jar
```

In another terminal, request the two endpoints:

```bash
curl http://localhost:8080/catalog
curl http://localhost:8080/shipping/quote
```

The catalog contains `Robot arm`, `Conveyor belt`, and `Safety sensor`. The shipping response is `{"service":"standard","price":12}`. Stop the application with Ctrl+C before continuing. The remaining host commands run from the companion directory, one level above `lab`.

## Prepare the workstation template

Install and initialize ISX:

```bash
brew install Sanne/tap/incus-spawn
isx init
```

Configure the Bob credential during initialization. ISX keeps it in `~/.config/incus-spawn/config.yaml` on the host. The workstations use `BOB_API_KEY=bob-placeholder`, and the host HTTPS proxy supplies the real authentication. The optional VM and proxy services start at login if you enable them.

Create a standalone source repository and build the template:

```bash
./scripts/prepare-source.sh
isx build tpl-mainthread-bob21 --yes
```

The preparation script copies the application source, build files, prompts, and task runner into `.local/parallel-bob-lab`. It creates a starting commit and saves `.local/baseline.bundle` for the later host clone. Existing snapshots are preserved: the script refuses to overwrite one.

The template at `.incus-spawn/images/bob-lab.yaml` uses `mode: copy` to put that repository in `~/parallel-bob-lab`. The custom tools install Temurin and use Fedora's Java trust store so Maven can trust the ISX proxy certificate authority. The template also downloads the application's Maven dependencies by running its tests.

ISX 0.3.10's built-in Bob tool exports `BOBSHELL_API_KEY`; Bob Shell 2.0.5 uses `BOB_API_KEY` for `bob run`. The template sets the latter to the placeholder. Keep the real credential out of the template, prompts, and repository.

## Create a workstation for each task

Create two instances with separate filesystems and two CPUs and 4 GB of memory each:

```bash
isx branch mt-catalog-agent --from tpl-mainthread-bob21 --cpu 2 --memory 4GB
isx branch mt-shipping-agent --from tpl-mainthread-bob21 --cpu 2 --memory 4GB
isx list
```

On the Mac, these Incus system containers share the Linux appliance's kernel. Each instance receives its own copied repository and Maven cache. Open an interactive shell when you need to inspect an instance:

```bash
isx shell mt-catalog-agent
```

## Run the Bob tasks

From a host terminal in the companion directory, start both SSH sessions:

```bash
ssh -F "$HOME/.config/incus-spawn/ssh/config" mt-catalog-agent \
  'bash -lc "bash ~/parallel-bob-lab/scripts/run-task.sh catalog"' \
  > .local/catalog-run.log 2>&1 &
catalog_pid=$!

ssh -F "$HOME/.config/incus-spawn/ssh/config" mt-shipping-agent \
  'bash -lc "bash ~/parallel-bob-lab/scripts/run-task.sh shipping"' \
  > .local/shipping-run.log 2>&1 &
shipping_pid=$!

wait "$catalog_pid"
wait "$shipping_pid"
```

The runner reads the matching prompt from `lab/prompts/`, allows up to 20 turns and one Bobcoin per task, and disables MCP and subagents. `bob run` pre-approves tool calls inside the guest. The cost limit bounds each Bob session's spending; the placeholder still allows requests through the host proxy.

After Bob finishes, the runner repeats the application tests, checks whitespace, and commits the changed Java source and tests. A failed Bob session or test stops the runner before its commit. Session output stays in the guest's `~/run-results`, outside its repository; the host SSH logs stay in `.local/`.

## Fetch and review the changes

Keep both workstations running while fetching their feature branches:

```bash
./scripts/fetch-results.sh
less .local/catalog.diff
less .local/shipping.diff
```

The fetch script creates `.local/review` from the baseline bundle, adds the workstations as `isx://` Git remotes, and saves each fetched commit ID. It refuses to overwrite an existing review checkout.

Inspect how the catalog handles missing, blank, and case-insensitive queries, and how shipping handles express and unsupported speeds. Building the fetched project executes its code, so read the diffs before accepting the changes.

To accept both commits into the host review checkout and test the resulting application:

```bash
catalog_commit=$(cat .local/catalog-commit.txt)
shipping_commit=$(cat .local/shipping-commit.txt)
git -C .local/review cherry-pick "$catalog_commit" "$shipping_commit"
cd .local/review
./mvnw -B verify -DskipITs=false
```

You can instead accept one commit, edit a change, or leave it aside. The saved commit IDs identify the versions you fetched even if an agent later changes its branch.

## Remove the workstations

After fetching the commits you want to keep, remove the two instances:

```bash
isx destroy mt-catalog-agent --skip-confirmation
isx destroy mt-shipping-agent --skip-confirmation
```

The host review checkout retains the commits. The stopped template remains available for another task:

```bash
isx branch mt-fresh-agent --from tpl-mainthread-bob21 --cpu 2 --memory 4GB
isx shell mt-fresh-agent
```

When you finish with that instance, remove it too:

```bash
isx destroy mt-fresh-agent --skip-confirmation
```

The Linux appliance, proxy services, and host credential have a separate lifecycle. Inspect `isx reset --help` before a full reset; it affects all ISX environments. See the [ISX documentation](https://isx.run/docs) for host lifecycle options.

For a new complete run, archive your previous `.local/` directory or use a fresh companion checkout before preparing the baseline again. To change the source inherited by future workstations, prepare the updated application and rebuild the template.
