# Shipping quote companion

This is a Java 25 Quarkus application built with the Maven wrapper. Run commands
from this directory. No database or containers are required.

- Standard shipping costs EUR 4.99 below a subtotal of EUR 100.00 and is free at
  or above EUR 100.00. Express shipping costs EUR 9.99 regardless of subtotal.
- Use `./mvnw --batch-mode --no-transfer-progress verify -DskipITs=false` for full
  verification, including tests against the packaged application.
- Muninn is registered in `.mvn/extensions.xml`. Set `MUNINN_OUTPUT=json` for
  structured build feedback or `MUNINN_OUTPUT=off` for ordinary console output.
- With JSON enabled, start with the report. Read the referenced full log or test
  reports when the summary does not answer your diagnostic question.
- A successful build must run the intended tests. Do not disable tests or weaken
  their assertions to repair an implementation defect.
- Keep application code idiomatic Quarkus: constructor injection, typed
  configuration, four-space indentation, and explicit imports.
