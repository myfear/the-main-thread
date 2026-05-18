# OrderBridge graceful shutdown demo

Companion app for the article **The 503 You Didn't Know You Were Sending: Quarkus Graceful Shutdown Done Right**.

```bash
./mvnw test
./mvnw verify
./scripts/demonstrate-shutdown.sh naive
./scripts/demonstrate-shutdown.sh graceful
```

Full walkthrough: [article.md](article.md).
