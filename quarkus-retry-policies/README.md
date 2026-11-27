# Measuring Quarkus retry policies

Run five policies against a local shipping quote service and compare the number of HTTP requests with the time the caller waited. The experiment uses Quarkus 4.0.0.Beta1 and SmallRye Fault Tolerance 7.0.0. [Read *How Much Does a Quarkus Retry Cost?*](https://www.the-main-thread.com/p/measuring-quarkus-retry-policies)

## Run the comparison

Select JDK 25 and open the directory containing `pom.xml`. The Maven wrapper downloads Maven and the project dependencies on first use. The test starts its own HTTP server on an available loopback port.

```sh
java -version
./mvnw test
```

On Windows, run `mvnw.cmd test`. A successful test writes 105 measured calls to `target/operations.csv`. Each policy runs against three faults, with two warm-up rounds and seven recorded rounds.

- `first_two_503` returns 503 on the first two GETs for a call, then 200.
- `outage_250ms` returns 503 until 250 ms have passed since the first GET for that call.
- `always_503` returns 503 for every request.

Open the CSV in a spreadsheet, or use this command to see all five policies in the first measured round of the timed outage.

```sh
grep '^1,outage_250ms,' target/operations.csv
```

`requests` counts GETs received by the carrier. `outcome` records the last HTTP status seen by the caller, including a final 503 after the client throws. `elapsed_ms` measures the whole guarded call. `arrival_ms` gives the time of each GET relative to the first one at the carrier. The entries in `statuses` correspond to those arrivals in order.

## Change the recovery time

Save a copy of the first CSV if you want to compare runs. The following command writes fresh measurements to the same file, with the timed scenario labeled `outage_500ms`.

```sh
./mvnw test -Dretry.lab.recoveryMs=500
```

To change the base delay, the cap or the jitter, edit the annotations in [QuoteClient.java](src/main/java/com/themainthread/retry/QuoteClient.java). [RetryPolicyTest.java](src/test/java/com/themainthread/retry/RetryPolicyTest.java) defines the three faults, starts a fresh sequence for each logical call and records the requests. The calls run one at a time. Local HTTP and scheduling times will vary between runs.
