package com.mundolimpio.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DefaultDockerCmdExecFactory;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.InvalidConfigurationException;
import org.testcontainers.dockerclient.TransportConfig;

import java.net.URI;

/**
 * WHAT: Estrategia personalizada de DockerClientProvider que fuerza el transporte HTTP/5 TCP
 *       en vez del zerodep (npipe) que hardcodea docker-java en Windows.
 *
 * WHY: docker-java-transport-zerodep hardcodea npipe en Windows e ignora completamente
 *      DOCKER_HOST. Al excluir zerodep de las dependencias y agregar httpclient5 + core,
 *      necesitamos una estrategia que cree el DockerClient usando httpclient5 explícitamente.
 *      Las estrategias built-in de Testcontainers referencian ZerodepDockerHttpClient
 *      y fallan con NoClassDefFoundError cuando zerodep está excluido.
 *
 * HOW: Construye manualmente un ApacheDockerHttpClient (httpclient5) y lo inyecta
 *      en DockerClientImpl.getInstance(config, httpClient). Luego agrega
 *      DefaultDockerCmdExecFactory para que los comandos Docker funcionen.
 *      Esto bypassea completamente el mecanismo de detección de transporte
 *      que docker-java-core usa por defecto.
 *
 * DIFFERENCES:
 *      - Antes: WindowsTcpDockerClientProviderStrategy (zerodep overrideaba a npipe)
 *      - Ahora: Httpclient5TcpDockerClientProviderStrategy (httpclient5 explícito, zerodep excluido)
 */
public class Httpclient5TcpDockerClientProviderStrategy extends DockerClientProviderStrategy {

    private static final URI DOCKER_HOST;

    static {
        String host = System.getenv().getOrDefault("DOCKER_HOST", "tcp://localhost:2375");
        DOCKER_HOST = URI.create(host);
    }

    private DockerClient client;

    @Override
    public TransportConfig getTransportConfig() throws InvalidConfigurationException {
        TransportConfig config = TransportConfig.builder()
            .dockerHost(DOCKER_HOST)
            .build();
        // Construir el cliente temprano para validar conectividad
        try {
            client = buildHttpclient5Client();
            // Validar que la conexión funciona
            client.pingCmd().exec();
        } catch (Exception e) {
            throw new InvalidConfigurationException(
                "No se pudo conectar a Docker via HTTP/5 en " + DOCKER_HOST, e
            );
        }
        return config;
    }

    @Override
    public DockerClient getDockerClient() {
        return client;
    }

    @Override
    protected boolean isApplicable() {
        // WHAT: Solo activar cuando DOCKER_HOST está explícitamente configurado como TCP.
        // WHY: En CI (Linux), DOCKER_HOST podría no estar seteado o estar seteado
        //      por surefire sin un endpoint TCP real. Si forzamos la conexión TCP
        //      en ese caso, falla y bloquea la cadena de estrategias.
        //      Tirando InvalidConfigurationException, Testcontainers saltea esta
        //      estrategia y continúa con UnixSocketClientProviderStrategy (zerodep).
        // DIFFERENCES: Antes siempre retornaba true, forzando conexión TCP
        //              incluso cuando no había endpoint TCP disponible.
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || !dockerHost.startsWith("tcp://")) {
            throw new InvalidConfigurationException(
                "DOCKER_HOST not set to TCP — skipping"
            );
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Httpclient5 TCP — " + DOCKER_HOST;
    }

    @Override
    protected int getPriority() {
        // Prioridad máxima para que se use antes que las estrategias built-in
        // (que fallan con NoClassDefFoundError porque zerodep está excluido).
        return Integer.MAX_VALUE;
    }

    /**
     * Construye un DockerClient usando docker-java-transport-httpclient5 explícitamente.
     * 
     * Paso 1: Crea ApacheDockerHttpClient (transporte HTTP/5) apuntando al DOCKER_HOST TCP.
     * Paso 2: Crea DockerClientImpl con el transporte HTTP/5 explícito (sin auto-detección).
     * Paso 3: Agrega DefaultDockerCmdExecFactory(httpClient, ObjectMapper) para ejecutar comandos Docker.
     * 
     * Esto bypassea completamente la detección automática de docker-java-core
     * que de otro modo buscaría zerodep primero y fallaría con NoClassDefFoundError.
     */
    private DockerClient buildHttpclient5Client() {
        // Paso 1: Transporte HTTP/5 explícito
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(DOCKER_HOST)
            .build();

        // Paso 2: DockerClientConfig mínimo (solo necesita el host)
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(DOCKER_HOST.toString())
            .build();

        // Paso 3: Crear DockerClient con transporte explícito + comando exec factory
        // getInstance(config, httpClient) retorna DockerClient (interfaz) pero la
        // implementación subyacente es DockerClientImpl, que tiene withDockerCmdExecFactory.
        //
        // ObjectMapper configurado para compatibilidad con Docker Desktop 4.73.1:
        // - FAIL_ON_UNKNOWN_PROPERTIES=false: docker-java 3.4.1 no tiene campos
        //   nuevos de Docker Engine API 1.47+ (ej: PidsLimit en Info).
        // - FAIL_ON_EMPTY_BEANS=false: CreateContainerCmd usa Map<String, Object>
        //   para ExposedPorts con valores de tipo Object vacíos ({} en JSON).
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        DockerClientImpl clientImpl = (DockerClientImpl)
            DockerClientImpl.getInstance(config, httpClient);
        return clientImpl.withDockerCmdExecFactory(
            new DefaultDockerCmdExecFactory(httpClient, objectMapper)
        );
    }
}
