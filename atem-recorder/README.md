# ATEM Recorder

Record a hardware switcher's program feed to a Mac and download the result
as an MP4. MediaMTX receives RTMP and writes the recording to disk. Quarkus
shows the stream status and serves downloads through a browser.

[Recording my basement studio with MediaMTX and Quarkus](https://www.the-main-thread.com/p/local-rtmp-recorder-mediamtx-quarkus)

## Prerequisites

- A Mac with a running Podman machine and Java 25 on your `PATH`.
- A RØDECaster Video S on the same trusted LAN, or FFmpeg with the `libx264`
  and AAC encoders to send a test pattern and tone.

Run the commands from the `atem-recorder` directory.

## Start MediaMTX

The supplied configuration uses the path `live/atem`. Create a local copy
for `live/rode` and start MediaMTX in its own terminal.

```bash
sed 's#live/atem#live/rode#g' mediamtx.yml > mediamtx-rode.yml
mkdir -p recordings/live/rode

podman run --rm --name atem-mediamtx \
  -p 1935:1935 \
  -p 127.0.0.1:9996:9996 \
  -p 127.0.0.1:9997:9997 \
  -v "$PWD/mediamtx-rode.yml:/mediamtx.yml:ro" \
  -v "$PWD/recordings:/recordings" \
  docker.io/bluenviron/mediamtx:1.21.3
```

The switcher connects over the LAN on TCP port 1935. The stream key identifies
the path, so use this setup on a trusted LAN. The playback and control APIs
listen on host loopback.

## Start the dashboard

Build and run the Java application in a second terminal.

```bash
./mvnw package
ATEM_STREAM_PATH=live/rode java -jar target/quarkus-app/quarkus-run.jar
```

Open the [recorder dashboard](http://127.0.0.1:8080/) on the Mac running
Quarkus. It shows **Waiting for stream** until a publisher connects.

## Send a stream and download the recording

In the RØDECaster App, add a stream profile. Set **Server** to
`rtmp://MAC_LAN_IP:1935/live` and **Stream Key** to `rode`. Replace
`MAC_LAN_IP` with the LAN address of the Mac running MediaMTX. Save and select
the profile on the RØDECaster, then tap **Go Live** and confirm. The RØDECaster
appends the key to the server URL, giving the path `live/rode`.

To try the recorder without the switcher, run this command in a third
terminal to send a 30-second test pattern and tone.

```bash
ATEM_RTMP_URL=rtmp://127.0.0.1:1935/live/rode ./scripts/publish-test-stream.sh 30
```

While the feed is arriving, the dashboard shows **Stream connected**. After
you end the stream or the script exits, look for the new entry under
**Recordings**. Select **Refresh** to check immediately, then select
**Download MP4**. Play the downloaded file to check picture and sound.

If the switcher stays at **Connecting**, check the MediaMTX terminal for the
requested path. An attempt to publish to `live/rode/rode` means the stream
key is already in the server field. If no connection appears in the log,
verify the Mac's LAN address and that TCP port 1935 is reachable.

Stop Quarkus and MediaMTX with Ctrl+C in their respective terminals.
The segments in `recordings/live/rode/` survive a restart. Remove files
from disk when you no longer need them.

## Use another RTMP path

Set the MediaMTX path, publish and playback permissions, and
`ATEM_STREAM_PATH` to match what your encoder publishes. To try the
bundled `live/atem` setup with FFmpeg on the Mac, create
`recordings/live/atem/` and start MediaMTX with `./scripts/mediamtx.sh`.
Run Quarkus without a path override and publish with
`./scripts/publish-test-stream.sh 30`. The launcher binds RTMP to loopback
by default. Set `ATEM_RTMP_BIND=0.0.0.0` when a trusted LAN encoder
needs to reach it.

## Run the API tests

`./mvnw test` runs the Quarkus API tests against a local Java stub for
MediaMTX. The application uses [Quarkus REST](https://quarkus.io/guides/rest),
[Qute](https://quarkus.io/guides/qute), and the
[MediaMTX playback API](https://mediamtx.org/docs/features/playback).
