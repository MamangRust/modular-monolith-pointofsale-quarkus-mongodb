package com.sanedge.common.test;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class MongoDbResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(MongoDbResource.class);
    private GenericContainer<?> mongo;

    // Set Docker host BEFORE any Testcontainers code loads (static initializer runs at class load time)
    static {
        System.setProperty("docker.host", "unix:///var/run/docker.sock");
    }

    @Override
    public Map<String, String> start() {

        try {
            // Verify Docker is actually reachable before attempting container start
            DockerClientFactory.instance().client();

            mongo = new GenericContainer<>(DockerImageName.parse("mongo:7"))
                    .withExposedPorts(27017)
                    .waitingFor(Wait.forListeningPort());

            mongo.start();

            int port = mongo.getMappedPort(27017);
            String host = mongo.getHost();

            return Map.of(
                    "quarkus.mongodb.connection-string", "mongodb://" + host + ":" + port,
                    "quarkus.mongodb.database", "test"
            );
        } catch (Exception e) {
            log.warn("Docker/Testcontainers tidak tersedia. Skipping test yang bergantung pada MongoDbResource. Error: {}", e.getMessage());
            // Return empty config - test harus handle dengan Assumptions.assumeTrue()
            return Map.of(
                    "docker.available", "false"
            );
        }
    }

    @Override
    public void stop() {
        if (mongo != null) {
            try {
                mongo.stop();
            } catch (Exception e) {
                log.warn("Gagal stop container: {}", e.getMessage());
            }
        }
    }

    public static boolean isDockerAvailable() {
        try {
            System.setProperty("docker.host", "unix:///var/run/docker.sock");
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
