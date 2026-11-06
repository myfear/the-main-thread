# Install the workshop model

You need the organizer's model registry URL, Maven cache URL, and participant files, plus local Ollama 0.40.13 or newer and Java 25. Install the tools and extract the files before the event. The organizer prepares the Pi's Maven cache; your build downloads its dependencies over the LAN. Keep Ollama running on your laptop.

## macOS or Linux

Install Bash, curl, and jq if needed. From the participant directory, use the URL supplied by your organizer:

```bash
bash scripts/setup-participant.sh --registry http://192.168.1.20:5002
ollama show workshop-qwen:1.7b
cd app
export WORKSHOP_MAVEN_URL=http://192.168.1.20:5003/central
export MVNW_REPOURL="$WORKSHOP_MAVEN_URL"
./mvnw -s settings-workshop.xml quarkus:dev
```

## Windows

Open Ollama, then run PowerShell in the participant directory:

```powershell
.\scripts\setup-participant.ps1 -Registry http://192.168.1.20:5002
ollama show workshop-qwen:1.7b
cd app
$env:WORKSHOP_MAVEN_URL = "http://192.168.1.20:5003/central"
$env:MVNW_REPOURL = $env:WORKSHOP_MAVEN_URL
.\mvnw.cmd -s settings-workshop.xml quarkus:dev
```

Use the organizer's address. Follow your organization's script policy if execution is blocked. This kit does not change PowerShell execution policy. The script is retained from the original kit; native Windows execution was not verified for this Pi walkthrough.

If using WSL2 instead, run Ollama and the Bash procedure inside WSL2; its model store is separate from native Windows Ollama.

## Ask for a code review

In a second terminal, from `app/`:

```bash
curl -fsS http://127.0.0.1:8080/review \
  -H 'Content-Type: application/json' \
  --data-binary @review-request.json
```

In PowerShell, use:

```powershell
Invoke-RestMethod -Uri http://127.0.0.1:8080/review -Method Post `
  -ContentType application/json -Body (Get-Content -Raw review-request.json)
```

The answer should identify the invalid final array index and suggest using `i < numbers.length`. Wording can vary. The app supplies advice, not executed code or a correctness guarantee.

## Understand the addresses

`http://PI_ADDRESS:5002` distributes model files. `http://PI_ADDRESS:5003/central` supplies Maven and the Java build dependencies. Set both Maven environment variables in the terminal where you run the wrapper, and pass `-s settings-workshop.xml` for each workshop build. The supplied settings do not change your global Maven configuration. Your application uses laptop Ollama at `http://127.0.0.1:11434`. Do not point `OLLAMA_HOST` at the Pi. Once installed, the model can run without the registry.

Setup verifies the organizer's manifest digest, then creates `workshop-qwen:1.7b`. Repeating setup with the same model does not download the blobs again. It still needs to reach the registry to check its manifest. An existing alias with different contents is preserved. Add `--replace-existing` (Bash) or `-ReplaceExisting` (PowerShell) only if you want to replace that alias.

If a registry digest changes, get the corresponding lock file from your organizer. If the app returns HTTP 503, confirm Ollama is running and `ollama show workshop-qwen:1.7b` succeeds. If setup cannot reach the Pi, confirm the workshop network allows laptop-to-Pi connections.

Stop Quarkus with Ctrl+C. To run its packaged form after preparing dependencies, use `./mvnw -s settings-workshop.xml package` and `java -jar target/quarkus-app/quarkus-run.jar` from `app/` (use `mvnw.cmd` on Windows).

If Maven cannot download an artifact, check the cache URL and ask the organizer to confirm it was preloaded for your platform. Retry with `-U` after the cause is fixed. Maven retains downloaded artifacts on your laptop; `-o` uses only that local cache and therefore prevents fetching missing artifacts from the Pi.
