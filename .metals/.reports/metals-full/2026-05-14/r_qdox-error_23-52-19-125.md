error id: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/service/NatsService.java
file://<WORKSPACE>/src/main/java/org/serwin/auth_server/service/NatsService.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[61,62]

error in qdox parser
file content:
```java
offset: 1913
uri: file://<WORKSPACE>/src/main/java/org/serwin/auth_server/service/NatsService.java
text:
```scala
package org.serwin.auth_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsService {

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.username:}")
    private String natsUsername;

    @Value("${nats.password:}")
    private String natsPassword;

    @Value("${spring.profiles.active:dev}")
    private String env;

    private final ObjectMapper objectMapper;
    private Connection natsConnection;

    @PostConstruct
    public void init() {
        try {
            Options.Builder builder = new Options.Builder()
                    .server(natsUrl)
                    .connectionName("auth-server")
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2));

            if (natsUsername != null && !natsUsername.isEmpty()) {
                builder.userInfo(natsUsername, natsPassword);
            }

            natsConnection = Nats.connect(builder.build());
            log.info("Connected to NATS at {} with environment prefix: {}", natsUrl, env);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to connect to NATS: {}", e.getMessage());
        }
    }

    /**
     * High-level publish method using the standardized scheme:
     * <env>.auth.v1.<domain>.<action>
     */
    public void publish(String domain, String action, string ,@@ Object payload) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS not connected, skipping publish to domain: {}", domain);
            return;
        }
log.debug("Publishing event to domain: {}, action: {} in env: {}", domain, action,env);
        String subject = String.format("%s.v1.%s.%s", env, domain, action);
        try {
            String json = objectMapper.writeValueAsString(payload);
            natsConnection.publish(subject, json.getBytes());
            log.debug("Published NATS event to subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to publish to subject {}: {}", subject, e.getMessage());
        }
    }

    /**
     * Synchronous request-reply method.
     */
    public <T> T request(String subject, Object payload, Class<T> responseType) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS not connected, skipping request to subject: {}", subject);
            return null;
        }

        try {
            byte[] data = objectMapper.writeValueAsBytes(payload);
            java.util.concurrent.CompletableFuture<Message> future = natsConnection.request(subject, data);
            Message reply = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            if (reply != null && reply.getData() != null) {
                return objectMapper.readValue(reply.getData(), responseType);
            }
        } catch (Exception e) {
            log.error("NATS request failed for subject {}: {}", subject, e.getMessage());
        }
        return null;
    }

    /**
     * Get the current environment prefix for topics
     */
    public String getEnv() {
        return env;
    }

    /**
     * Direct connection access for the listener
     */
    public Connection getConnection() {
        return natsConnection;
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/org/serwin/auth_server/service/NatsService.java