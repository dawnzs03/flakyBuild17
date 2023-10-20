package io.smallrye.reactive.messaging.rabbitmq;

import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.INCOMING;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.INCOMING_AND_OUTGOING;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.OUTGOING;
import static io.smallrye.reactive.messaging.rabbitmq.i18n.RabbitMQExceptions.ex;
import static io.smallrye.reactive.messaging.rabbitmq.i18n.RabbitMQLogging.log;
import static java.time.Duration.ofSeconds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.impl.CredentialsProvider;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.smallrye.reactive.messaging.annotations.ConnectorAttribute;
import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;
import io.smallrye.reactive.messaging.health.HealthReport;
import io.smallrye.reactive.messaging.health.HealthReporter;
import io.smallrye.reactive.messaging.providers.connectors.ExecutionHolder;
import io.smallrye.reactive.messaging.providers.helpers.CDIUtils;
import io.smallrye.reactive.messaging.providers.helpers.MultiUtils;
import io.smallrye.reactive.messaging.rabbitmq.ack.RabbitMQAck;
import io.smallrye.reactive.messaging.rabbitmq.ack.RabbitMQAckHandler;
import io.smallrye.reactive.messaging.rabbitmq.ack.RabbitMQAutoAck;
import io.smallrye.reactive.messaging.rabbitmq.fault.RabbitMQFailureHandler;
import io.smallrye.reactive.messaging.rabbitmq.tracing.RabbitMQOpenTelemetryInstrumenter;
import io.smallrye.reactive.messaging.rabbitmq.tracing.RabbitMQTrace;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.rabbitmq.RabbitMQClient;
import io.vertx.mutiny.rabbitmq.RabbitMQConsumer;
import io.vertx.mutiny.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;

@ApplicationScoped
@Connector(RabbitMQConnector.CONNECTOR_NAME)

// RabbitMQClient configuration
@ConnectorAttribute(name = "username", direction = INCOMING_AND_OUTGOING, description = "The username used to authenticate to the broker", type = "string", alias = "rabbitmq-username")
@ConnectorAttribute(name = "password", direction = INCOMING_AND_OUTGOING, description = "The password used to authenticate to the broker", type = "string", alias = "rabbitmq-password")
@ConnectorAttribute(name = "host", direction = INCOMING_AND_OUTGOING, description = "The broker hostname", type = "string", alias = "rabbitmq-host", defaultValue = "localhost")
@ConnectorAttribute(name = "port", direction = INCOMING_AND_OUTGOING, description = "The broker port", type = "int", alias = "rabbitmq-port", defaultValue = "5672")
@ConnectorAttribute(name = "ssl", direction = INCOMING_AND_OUTGOING, description = "Whether or not the connection should use SSL", type = "boolean", alias = "rabbitmq-ssl", defaultValue = "false")
@ConnectorAttribute(name = "trust-all", direction = INCOMING_AND_OUTGOING, description = "Whether to skip trust certificate verification", type = "boolean", alias = "rabbitmq-trust-all", defaultValue = "false")
@ConnectorAttribute(name = "trust-store-path", direction = INCOMING_AND_OUTGOING, description = "The path to a JKS trust store", type = "string", alias = "rabbitmq-trust-store-path")
@ConnectorAttribute(name = "trust-store-password", direction = INCOMING_AND_OUTGOING, description = "The password of the JKS trust store", type = "string", alias = "rabbitmq-trust-store-password")
@ConnectorAttribute(name = "connection-timeout", direction = INCOMING_AND_OUTGOING, description = "The TCP connection timeout (ms); 0 is interpreted as no timeout", type = "int", defaultValue = "60000")
@ConnectorAttribute(name = "handshake-timeout", direction = INCOMING_AND_OUTGOING, description = "The AMQP 0-9-1 protocol handshake timeout (ms)", type = "int", defaultValue = "10000")
@ConnectorAttribute(name = "automatic-recovery-enabled", direction = INCOMING_AND_OUTGOING, description = "Whether automatic connection recovery is enabled", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "automatic-recovery-on-initial-connection", direction = INCOMING_AND_OUTGOING, description = "Whether automatic recovery on initial connections is enabled", type = "boolean", defaultValue = "true")
@ConnectorAttribute(name = "reconnect-attempts", direction = INCOMING_AND_OUTGOING, description = "The number of reconnection attempts", type = "int", alias = "rabbitmq-reconnect-attempts", defaultValue = "100")
@ConnectorAttribute(name = "reconnect-interval", direction = INCOMING_AND_OUTGOING, description = "The interval (in seconds) between two reconnection attempts", type = "int", alias = "rabbitmq-reconnect-interval", defaultValue = "10")
@ConnectorAttribute(name = "network-recovery-interval", direction = INCOMING_AND_OUTGOING, description = "How long (ms) will automatic recovery wait before attempting to reconnect", type = "int", defaultValue = "5000")
@ConnectorAttribute(name = "user", direction = INCOMING_AND_OUTGOING, description = "The user name to use when connecting to the broker", type = "string", defaultValue = "guest")
@ConnectorAttribute(name = "include-properties", direction = INCOMING_AND_OUTGOING, description = "Whether to include properties when a broker message is passed on the event bus", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "requested-channel-max", direction = INCOMING_AND_OUTGOING, description = "The initially requested maximum channel number", type = "int", defaultValue = "2047")
@ConnectorAttribute(name = "requested-heartbeat", direction = INCOMING_AND_OUTGOING, description = "The initially requested heartbeat interval (seconds), zero for none", type = "int", defaultValue = "60")
@ConnectorAttribute(name = "use-nio", direction = INCOMING_AND_OUTGOING, description = "Whether usage of NIO Sockets is enabled", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "virtual-host", direction = INCOMING_AND_OUTGOING, description = "The virtual host to use when connecting to the broker", type = "string", defaultValue = "/", alias = "rabbitmq-virtual-host")
@ConnectorAttribute(name = "client-options-name", direction = INCOMING_AND_OUTGOING, description = "The name of the RabbitMQ Client Option bean used to customize the RabbitMQ client configuration", type = "string", alias = "rabbitmq-client-options-name")
@ConnectorAttribute(name = "credentials-provider-name", direction = INCOMING_AND_OUTGOING, description = "The name of the RabbitMQ Credentials Provider bean used to provide dynamic credentials to the RabbitMQ client", type = "string", alias = "rabbitmq-credentials-provider-name")

// Exchange
@ConnectorAttribute(name = "exchange.name", direction = INCOMING_AND_OUTGOING, description = "The exchange that messages are published to or consumed from. If not set, the channel name is used. If set to \"\", the default exchange is used.", type = "string")
@ConnectorAttribute(name = "exchange.durable", direction = INCOMING_AND_OUTGOING, description = "Whether the exchange is durable", type = "boolean", defaultValue = "true")
@ConnectorAttribute(name = "exchange.auto-delete", direction = INCOMING_AND_OUTGOING, description = "Whether the exchange should be deleted after use", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "exchange.type", direction = INCOMING_AND_OUTGOING, description = "The exchange type: direct, fanout, headers or topic (default)", type = "string", defaultValue = "topic")
@ConnectorAttribute(name = "exchange.declare", direction = INCOMING_AND_OUTGOING, description = "Whether to declare the exchange; set to false if the exchange is expected to be set up independently", type = "boolean", defaultValue = "true")

// Queue
@ConnectorAttribute(name = "queue.name", direction = INCOMING, description = "The queue from which messages are consumed.", type = "string", mandatory = true)
@ConnectorAttribute(name = "queue.durable", direction = INCOMING, description = "Whether the queue is durable", type = "boolean", defaultValue = "true")
@ConnectorAttribute(name = "queue.exclusive", direction = INCOMING, description = "Whether the queue is for exclusive use", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "queue.auto-delete", direction = INCOMING, description = "Whether the queue should be deleted after use", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "queue.declare", direction = INCOMING, description = "Whether to declare the queue and binding; set to false if these are expected to be set up independently", type = "boolean", defaultValue = "true")
@ConnectorAttribute(name = "queue.ttl", direction = INCOMING, description = "If specified, the time (ms) for which a message can remain in the queue undelivered before it is dead", type = "long")
@ConnectorAttribute(name = "queue.single-active-consumer", direction = INCOMING, description = "If set to true, only one consumer can actively consume messages", type = "boolean")
@ConnectorAttribute(name = "queue.x-queue-type", direction = INCOMING, description = "If automatically declare queue, we can choose different types of queue [quorum, classic, stream]", type = "string")
@ConnectorAttribute(name = "queue.x-queue-mode", direction = INCOMING, description = "If automatically declare queue, we can choose different modes of queue [lazy, default]", type = "string")
@ConnectorAttribute(name = "max-outgoing-internal-queue-size", direction = OUTGOING, description = "The maximum size of the outgoing internal queue", type = "int")
@ConnectorAttribute(name = "max-incoming-internal-queue-size", direction = INCOMING, description = "The maximum size of the incoming internal queue", type = "int", defaultValue = "500000")
@ConnectorAttribute(name = "connection-count", direction = INCOMING, description = "The number of RabbitMQ connections to create for consuming from this queue. This might be necessary to consume from a sharded queue with a single client.", type = "int", defaultValue = "1")
@ConnectorAttribute(name = "queue.x-max-priority", direction = INCOMING, description = "Define priority level queue consumer", type = "int")
@ConnectorAttribute(name = "queue.x-delivery-limit", direction = INCOMING, description = "If queue.x-queue-type is quorum, when a message has been returned more times than the limit the message will be dropped or dead-lettered", type = "long")

// DLQs
@ConnectorAttribute(name = "auto-bind-dlq", direction = INCOMING, description = "Whether to automatically declare the DLQ and bind it to the binder DLX", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "dead-letter-queue-name", direction = INCOMING, description = "The name of the DLQ; if not supplied will default to the queue name with '.dlq' appended", type = "string")
@ConnectorAttribute(name = "dead-letter-exchange", direction = INCOMING, description = "A DLX to assign to the queue. Relevant only if auto-bind-dlq is true", type = "string", defaultValue = "DLX")
@ConnectorAttribute(name = "dead-letter-exchange-type", direction = INCOMING, description = "The type of the DLX to assign to the queue. Relevant only if auto-bind-dlq is true", type = "string", defaultValue = "direct")
@ConnectorAttribute(name = "dead-letter-routing-key", direction = INCOMING, description = "A dead letter routing key to assign to the queue; if not supplied will default to the queue name", type = "string")
@ConnectorAttribute(name = "dlx.declare", direction = INCOMING, description = "Whether to declare the dead letter exchange binding. Relevant only if auto-bind-dlq is true; set to false if these are expected to be set up independently", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "dead-letter-queue-type", direction = INCOMING, description = "If automatically declare DLQ, we can choose different types of DLQ [quorum, classic, stream]", type = "string")
@ConnectorAttribute(name = "dead-letter-queue-mode", direction = INCOMING, description = "If automatically declare DLQ, we can choose different modes of DLQ [lazy, default]", type = "string")
@ConnectorAttribute(name = "dead-letter-ttl", direction = INCOMING, description = "If specified, the time (ms) for which a message can remain in DLQ undelivered before it is dead. Relevant only if auto-bind-dlq is true", type = "long")
@ConnectorAttribute(name = "dead-letter-dlx", direction = INCOMING, description = "If specified, a DLX to assign to the DLQ. Relevant only if auto-bind-dlq is true", type = "string")
@ConnectorAttribute(name = "dead-letter-dlx-routing-key", direction = INCOMING, description = "If specified, a dead letter routing key to assign to the DLQ. Relevant only if auto-bind-dlq is true", type = "string")

// Message consumer
@ConnectorAttribute(name = "failure-strategy", direction = INCOMING, description = "The failure strategy to apply when a RabbitMQ message is nacked. Accepted values are `fail`, `accept`, `reject` (default), `requeue` or name of a bean", type = "string", defaultValue = "reject")
@ConnectorAttribute(name = "broadcast", direction = INCOMING, description = "Whether the received RabbitMQ messages must be dispatched to multiple _subscribers_", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "auto-acknowledgement", direction = INCOMING, description = "Whether the received RabbitMQ messages must be acknowledged when received; if true then delivery constitutes acknowledgement", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "keep-most-recent", direction = INCOMING, description = "Whether to discard old messages instead of recent ones", type = "boolean", defaultValue = "false")
@ConnectorAttribute(name = "routing-keys", direction = INCOMING, description = "A comma-separated list of routing keys to bind the queue to the exchange. Relevant only if 'exchange.type' is topic or direct", type = "string", defaultValue = "#")
@ConnectorAttribute(name = "arguments", direction = INCOMING, description = "A comma-separated list of arguments [key1:value1,key2:value2,...] to bind the queue to the exchange. Relevant only if 'exchange.type' is headers", type = "string")
@ConnectorAttribute(name = "content-type-override", direction = INCOMING, description = "Override the content_type attribute of the incoming message, should be a valid MINE type", type = "string")
@ConnectorAttribute(name = "max-outstanding-messages", direction = INCOMING, description = "The maximum number of outstanding/unacknowledged messages being processed by the connector at a time; must be a positive number", type = "int")

// Message producer
@ConnectorAttribute(name = "max-inflight-messages", direction = OUTGOING, description = "The maximum number of messages to be written to RabbitMQ concurrently; must be a positive number", type = "long", defaultValue = "1024")
@ConnectorAttribute(name = "default-routing-key", direction = OUTGOING, description = "The default routing key to use when sending messages to the exchange", type = "string", defaultValue = "")
@ConnectorAttribute(name = "default-ttl", direction = OUTGOING, description = "If specified, the time (ms) sent messages can remain in queues undelivered before they are dead", type = "long")

// Tracing
@ConnectorAttribute(name = "tracing.enabled", direction = INCOMING_AND_OUTGOING, description = "Whether tracing is enabled (default) or disabled", type = "boolean", defaultValue = "true")
@ConnectorAttribute(name = "tracing.attribute-headers", direction = INCOMING_AND_OUTGOING, description = "A comma-separated list of headers that should be recorded as span attributes. Relevant only if tracing.enabled=true", type = "string", defaultValue = "")

public class RabbitMQConnector implements InboundConnector, OutboundConnector, HealthReporter {
    public static final String CONNECTOR_NAME = "smallrye-rabbitmq";

    private enum ChannelStatus {
        CONNECTED,
        NOT_CONNECTED,
        INITIALISING;
    }

    /**
     * The list of RabbitMQClient's currently managed by this connector
     */
    private final Map<String, RabbitMQClient> clients = new ConcurrentHashMap<>();

    /**
     * The list of RabbitMQ consumers.
     * This list is maintained to clean up the resources on termination.
     */
    private final List<RabbitMQConsumer> consumers = new CopyOnWriteArrayList<>();

    /**
     * The list of RabbitMQMessageSender's currently managed by this connector
     */
    private final Map<String, Flow.Subscription> subscriptions = new ConcurrentHashMap<>();

    // Keyed on channel name, value is the channel connection state
    private final Map<String, ChannelStatus> incomingChannelStatus = new ConcurrentHashMap<>();

    private final Map<String, ChannelStatus> outgoingChannelStatus = new ConcurrentHashMap<>();

    @Inject
    ExecutionHolder executionHolder;

    @Inject
    @Any
    Instance<RabbitMQOptions> clientOptions;

    @Inject
    @Any
    Instance<CredentialsProvider> credentialsProviders;

    private volatile RabbitMQOpenTelemetryInstrumenter instrumenter;

    @Inject
    @Any
    Instance<RabbitMQFailureHandler.Factory> failureHandlerFactories;

    RabbitMQConnector() {
        // used for proxies
    }

    public static String getExchangeName(final RabbitMQConnectorCommonConfiguration config) {
        return config.getExchangeName().map(s -> "\"\"".equals(s) ? "" : s).orElse(config.getChannel());
    }

    private Multi<? extends Message<?>> getStreamOfMessages(
            RabbitMQConsumer receiver,
            ConnectionHolder holder,
            RabbitMQConnectorIncomingConfiguration ic,
            RabbitMQFailureHandler onNack,
            RabbitMQAckHandler onAck) {

        final String queueName = ic.getQueueName();
        final boolean isTracingEnabled = ic.getTracingEnabled();
        final String contentTypeOverride = ic.getContentTypeOverride().orElse(null);
        log.receiverListeningAddress(queueName);

        if (isTracingEnabled) {
            return receiver.toMulti()
                    .map(m -> new IncomingRabbitMQMessage<>(m, holder, onNack, onAck, contentTypeOverride))
                    .map(msg -> instrumenter.traceIncoming(msg,
                            RabbitMQTrace.traceQueue(queueName, msg.message.envelope().getRoutingKey(),
                                    msg.getHeaders())));
        } else {
            return receiver.toMulti()
                    .map(m -> new IncomingRabbitMQMessage<>(m, holder, onNack, onAck, contentTypeOverride));
        }
    }

    /**
     * Creates a <em>channel</em> for the given configuration. The channel's configuration is associated with a
     * specific {@code connector}, using the {@link Connector} qualifier's parameter indicating a key to
     * which {@link IncomingConnectorFactory} to use.
     *
     * <p>
     * Note that the connection to the <em>transport</em> or <em>broker</em> is generally postponed until the
     * subscription occurs.
     *
     * @param config the configuration, must not be {@code null}, must contain the {@link #CHANNEL_NAME_ATTRIBUTE}
     *        attribute.
     * @return the created {@link Flow.Publisher}, will not be {@code null}.
     * @throws IllegalArgumentException if the configuration is invalid.
     * @throws NoSuchElementException if the configuration does not contain an expected attribute.
     */
    @Override
    public Flow.Publisher<? extends Message<?>> getPublisher(final Config config) {
        final RabbitMQConnectorIncomingConfiguration ic = new RabbitMQConnectorIncomingConfiguration(config);
        if (ic.getTracingEnabled() && instrumenter == null) {
            instrumenter = RabbitMQOpenTelemetryInstrumenter.createForConnector();
        }
        incomingChannelStatus.put(ic.getChannel(), ChannelStatus.INITIALISING);

        final RabbitMQFailureHandler onNack = createFailureHandler(ic);
        final RabbitMQAckHandler onAck = createAckHandler(ic);

        final Integer connectionCount = ic.getConnectionCount();
        Multi<? extends Message<?>> multi = Multi.createFrom().range(0, connectionCount)
                .onItem().transformToUniAndMerge(connectionIdx -> {
                    // Create a client
                    final RabbitMQClient client = RabbitMQClientHelper.createClient(this, ic, clientOptions,
                            credentialsProviders);
                    client.getDelegate().addConnectionEstablishedCallback(promise -> {
                        // Ensure we create the queues (and exchanges) from which messages will be read
                        Uni.createFrom().nullItem()
                                .onItem().call(ignored -> {
                                    if (ic.getMaxOutstandingMessages().isPresent()) {
                                        return client.basicQos(ic.getMaxOutstandingMessages().get(), false);
                                    } else {
                                        return Uni.createFrom().nullItem();
                                    }
                                })
                                .onItem().call(() -> establishQueue(client, ic))
                                .onItem().call(() -> establishDLQ(client, ic))
                                .subscribe().with(ignored -> promise.complete(), promise::fail);
                    });
                    final ConnectionHolder holder = new ConnectionHolder(client, ic, getVertx());
                    return holder.getOrEstablishConnection()
                            .invoke(() -> log.connectionEstablished(connectionIdx, ic.getChannel()))
                            .flatMap(connection -> createConsumer(ic, connection).map(consumer -> Tuple2.of(holder, consumer)));
                })
                // Wait for all consumers to be created/connected
                .collect().asList()
                .onItem().invoke(() -> incomingChannelStatus.put(ic.getChannel(), ChannelStatus.CONNECTED))
                // Translate all consumers into a merged stream of messages
                .onItem().transformToMulti(tuples -> Multi.createFrom().iterable(tuples))
                .flatMap(tuple -> getStreamOfMessages(tuple.getItem2(), tuple.getItem1(), ic, onNack, onAck));

        if (Boolean.TRUE.equals(ic.getBroadcast())) {
            multi = multi.broadcast().toAllSubscribers();
        }

        return multi;
    }

    private Uni<RabbitMQConsumer> createConsumer(RabbitMQConnectorIncomingConfiguration ic, RabbitMQClient client) {
        return client.basicConsumer(serverQueueName(ic.getQueueName()), new QueueOptions()
                .setAutoAck(ic.getAutoAcknowledgement())
                .setMaxInternalQueueSize(ic.getMaxIncomingInternalQueueSize())
                .setKeepMostRecent(ic.getKeepMostRecent()))
                .onItem().invoke(cons -> consumers.add(cons));
    }

    /**
     * Establish a DLQ, possibly establishing a DLX too
     *
     * @param client the {@link RabbitMQClient}
     * @param ic the {@link RabbitMQConnectorIncomingConfiguration}
     * @return a {@link Uni<String>} containing the DLQ name
     */
    private Uni<?> establishDLQ(final RabbitMQClient client, final RabbitMQConnectorIncomingConfiguration ic) {
        final String deadLetterQueueName = ic.getDeadLetterQueueName().orElse(String.format("%s.dlq", ic.getQueueName()));
        final String deadLetterExchangeName = ic.getDeadLetterExchange();
        final String deadLetterRoutingKey = ic.getDeadLetterRoutingKey().orElse(ic.getQueueName());

        // Declare the exchange if we have been asked to do so
        final Uni<String> dlxFlow = Uni.createFrom()
                .item(() -> ic.getAutoBindDlq() && ic.getDlxDeclare() ? null : deadLetterExchangeName)
                .onItem().ifNull().switchTo(() -> client.exchangeDeclare(deadLetterExchangeName, ic.getDeadLetterExchangeType(),
                        true, false)
                        .onItem().invoke(() -> log.dlxEstablished(deadLetterExchangeName))
                        .onFailure().invoke(ex -> log.unableToEstablishDlx(deadLetterExchangeName, ex))
                        .onItem().transform(v -> deadLetterExchangeName));

        // Declare the queue (and its binding to the exchange or DLQ type/mode) if we have been asked to do so
        final JsonObject queueArgs = new JsonObject();
        // x-dead-letter-exchange
        ic.getDeadLetterDlx().ifPresent(deadLetterDlx -> queueArgs.put("x-dead-letter-exchange", deadLetterDlx));
        // x-dead-letter-routing-key
        ic.getDeadLetterDlxRoutingKey().ifPresent(deadLetterDlx -> queueArgs.put("x-dead-letter-routing-key", deadLetterDlx));
        // x-queue-type
        ic.getDeadLetterQueueType().ifPresent(queueType -> queueArgs.put("x-queue-type", queueType));
        // x-queue-mode
        ic.getDeadLetterQueueMode().ifPresent(queueMode -> queueArgs.put("x-queue-mode", queueMode));
        // x-message-ttl
        ic.getDeadLetterTtl().ifPresent(queueTtl -> {
            if (queueTtl >= 0) {
                queueArgs.put("x-message-ttl", queueTtl);
            } else {
                throw ex.illegalArgumentInvalidQueueTtl();
            }
        });
        return dlxFlow
                .onItem().transform(v -> Boolean.TRUE.equals(ic.getAutoBindDlq()) ? null : deadLetterQueueName)
                .onItem().ifNull().switchTo(
                        () -> client
                                .queueDeclare(deadLetterQueueName, true, false, false, queueArgs)
                                .onItem().invoke(() -> log.queueEstablished(deadLetterQueueName))
                                .onFailure().invoke(ex -> log.unableToEstablishQueue(deadLetterQueueName, ex))
                                .onItem()
                                .call(v -> client.queueBind(deadLetterQueueName, deadLetterExchangeName, deadLetterRoutingKey))
                                .onItem()
                                .invoke(() -> log.deadLetterBindingEstablished(deadLetterQueueName, deadLetterExchangeName,
                                        deadLetterRoutingKey))
                                .onFailure()
                                .invoke(ex -> log.unableToEstablishBinding(deadLetterQueueName, deadLetterExchangeName, ex))
                                .onItem().transform(v -> deadLetterQueueName));
    }

    /**
     * Creates a <em>channel</em> for the given configuration. The channel's configuration is associated with a
     * specific {@code connector}, using the {@link Connector} qualifier's parameter indicating a key to
     * which {@link org.eclipse.microprofile.reactive.messaging.Outgoing} to use.
     * <p>
     * Note that the connection to the <em>transport</em> or <em>broker</em> is generally postponed until the
     * subscription.
     *
     * @param config the configuration, never {@code null}, must contain the {@link #CHANNEL_NAME_ATTRIBUTE}
     *        attribute.
     * @return the created {@link SubscriberBuilder}, must not be {@code null}.
     * @throws IllegalArgumentException if the configuration is invalid.
     * @throws NoSuchElementException if the configuration does not contain an expected attribute.
     */
    @Override
    public Flow.Subscriber<? extends Message<?>> getSubscriber(final Config config) {
        final RabbitMQConnectorOutgoingConfiguration oc = new RabbitMQConnectorOutgoingConfiguration(config);
        outgoingChannelStatus.put(oc.getChannel(), ChannelStatus.INITIALISING);

        // Create a client
        final RabbitMQClient client = RabbitMQClientHelper.createClient(this, oc, clientOptions, credentialsProviders);
        client.getDelegate().addConnectionEstablishedCallback(promise -> {
            // Ensure we create the exchange to which messages are to be sent
            establishExchange(client, oc).subscribe().with((ignored) -> promise.complete(), promise::fail);
        });

        final ConnectionHolder holder = new ConnectionHolder(client, oc, getVertx());
        final Uni<RabbitMQPublisher> getSender = holder.getOrEstablishConnection()
                .onItem().transformToUni(connection -> Uni.createFrom().item(RabbitMQPublisher.create(getVertx(), connection,
                        new RabbitMQPublisherOptions()
                                .setReconnectAttempts(oc.getReconnectAttempts())
                                .setReconnectInterval(ofSeconds(oc.getReconnectInterval()).toMillis())
                                .setMaxInternalQueueSize(oc.getMaxOutgoingInternalQueueSize().orElse(Integer.MAX_VALUE)))))
                // Start the publisher
                .onItem().call(RabbitMQPublisher::start)
                .invoke(s -> {
                    // Add the channel in the opened state
                    outgoingChannelStatus.put(oc.getChannel(), ChannelStatus.CONNECTED);
                })
                .onFailure().invoke(t -> outgoingChannelStatus.put(oc.getChannel(), ChannelStatus.NOT_CONNECTED))
                .onFailure().recoverWithNull()
                .memoize().indefinitely()
                .onCancellation().invoke(() -> outgoingChannelStatus.put(oc.getChannel(), ChannelStatus.NOT_CONNECTED));

        // Set up a sender based on the publisher we established above
        final RabbitMQMessageSender processor = new RabbitMQMessageSender(
                oc,
                getSender);
        subscriptions.put(oc.getChannel(), processor);

        // Return a SubscriberBuilder
        return MultiUtils.via(processor, m -> m.onFailure().invoke(t -> {
            log.error(oc.getChannel(), t);
            outgoingChannelStatus.put(oc.getChannel(), ChannelStatus.NOT_CONNECTED);
        }));
    }

    @Override
    public HealthReport getReadiness() {
        return getHealth(false);
    }

    @Override
    public HealthReport getLiveness() {
        return getHealth(false);
    }

    public HealthReport getHealth(boolean strict) {
        final HealthReport.HealthReportBuilder builder = HealthReport.builder();

        // Add health for incoming channels; since connections are made immediately
        // for subscribers, we insist on channel status being connected
        incomingChannelStatus.forEach((channel, status) -> builder.add(channel, status == ChannelStatus.CONNECTED));

        // Add health for outgoing channels; since connections are made only on
        // first message dispatch for publishers, we allow both connected and initialising
        // unless in strict mode, in order to avoid a possible deadly embrace in
        // kubernetes (k8s will refuse to allow the pod to accept traffic unless it is ready,
        // and only if it is ready can e.g. calls be made to it that would trigger a message dispatch).
        outgoingChannelStatus.forEach((channel, status) -> builder.add(channel,
                (strict) ? status == ChannelStatus.CONNECTED : status != ChannelStatus.NOT_CONNECTED));

        return builder.build();
    }

    /**
     * Application shutdown tidy up; cancels all subscriptions and stops clients.
     *
     * @param ignored the incoming event, ignored
     */
    public void terminate(
            @SuppressWarnings("unused") @Observes(notifyObserver = Reception.IF_EXISTS) @Priority(50) @BeforeDestroyed(ApplicationScoped.class) Object ignored) {
        subscriptions.forEach((channel, subscription) -> subscription.cancel());
        consumers.forEach(consumer -> {
            try {
                consumer.cancelAndAwait();
            } catch (AlreadyClosedException x) {
                // Ignoring - already closed.
            }
        });
        consumers.clear();
        clients.forEach((channel, rabbitMQClient) -> rabbitMQClient.stopAndAwait());
        clients.clear();
    }

    public Vertx getVertx() {
        return executionHolder.vertx();
    }

    public void addClient(String channel, RabbitMQClient client) {
        clients.put(channel, client);
    }

    public void establishQueue(String channel, RabbitMQConnectorIncomingConfiguration ic) {
        final RabbitMQClient client = clients.get(channel);
        client.getDelegate().addConnectionEstablishedCallback(promise -> {
            establishQueue(client, ic).subscribe().with((ignored) -> promise.complete(), promise::fail);
        });
    }

    /**
     * Uses a {@link RabbitMQClient} to ensure the required exchange is created.
     *
     * @param client the RabbitMQ client
     * @param config the channel configuration
     * @return a {@link Uni<String>} which yields the exchange name
     */
    private Uni<String> establishExchange(
            final RabbitMQClient client,
            final RabbitMQConnectorCommonConfiguration config) {
        final String exchangeName = getExchangeName(config);

        // Declare the exchange if we have been asked to do so and only when exchange name is not default ("")
        boolean declareExchange = Boolean.TRUE.equals(config.getExchangeDeclare()) && exchangeName.length() != 0;
        if (declareExchange) {
            return client.exchangeDeclare(exchangeName, config.getExchangeType(),
                    config.getExchangeDurable(), config.getExchangeAutoDelete())
                    .onItem().invoke(() -> log.exchangeEstablished(exchangeName))
                    .onFailure().invoke(ex -> log.unableToEstablishExchange(exchangeName, ex))
                    .onItem().transform(v -> exchangeName);
        } else {
            return Uni.createFrom().item(exchangeName);
        }
    }

    /**
     * Uses a {@link RabbitMQClient} to ensure the required queue-exchange bindings are created.
     *
     * @param client the RabbitMQ client
     * @param ic the incoming channel configuration
     * @return a {@link Uni<String>} which yields the queue name
     */
    private Uni<String> establishQueue(
            final RabbitMQClient client,
            final RabbitMQConnectorIncomingConfiguration ic) {
        final String queueName = ic.getQueueName();

        // Declare the queue (and its binding(s) to the exchange, and TTL) if we have been asked to do so
        final JsonObject queueArgs = new JsonObject();
        if (ic.getAutoBindDlq()) {
            queueArgs.put("x-dead-letter-exchange", ic.getDeadLetterExchange());
            queueArgs.put("x-dead-letter-routing-key", ic.getDeadLetterRoutingKey().orElse(queueName));
        }
        ic.getQueueSingleActiveConsumer().ifPresent(sac -> queueArgs.put("x-single-active-consumer", sac));
        // x-queue-type
        ic.getQueueXQueueType().ifPresent(queueType -> queueArgs.put("x-queue-type", queueType));
        // x-queue-mode
        ic.getQueueXQueueMode().ifPresent(queueMode -> queueArgs.put("x-queue-mode", queueMode));
        // x-message-ttl
        ic.getQueueTtl().ifPresent(queueTtl -> {
            if (queueTtl >= 0) {
                queueArgs.put("x-message-ttl", queueTtl);
            } else {
                throw ex.illegalArgumentInvalidQueueTtl();
            }
        });
        //x-max-priority
        ic.getQueueXMaxPriority().ifPresent(maxPriority -> queueArgs.put("x-max-priority", maxPriority));
        //x-delivery-limit
        ic.getQueueXDeliveryLimit().ifPresent(deliveryLimit -> queueArgs.put("x-delivery-limit", deliveryLimit));

        return establishExchange(client, ic)
                .onItem().transform(v -> Boolean.TRUE.equals(ic.getQueueDeclare()) ? null : queueName)
                // Not declaring the queue, so validate its existence...
                // Ensures RabbitMQClient is notified of invalid queues during connection cycle.
                .onItem().ifNotNull().call(name -> client.messageCount(name).onFailure().invoke(log::unableToConnectToBroker))
                // Declare the queue.
                .onItem().ifNull().switchTo(() -> {
                    String serverQueueName = serverQueueName(queueName);

                    Uni<AMQP.Queue.DeclareOk> declare;
                    if (serverQueueName.isEmpty()) {
                        declare = client.queueDeclare(serverQueueName, false, true, true);
                    } else {
                        declare = client.queueDeclare(serverQueueName, ic.getQueueDurable(),
                                ic.getQueueExclusive(), ic.getQueueAutoDelete(), queueArgs);
                    }

                    return declare
                            .onItem().invoke(() -> log.queueEstablished(queueName))
                            .onFailure().invoke(ex -> log.unableToEstablishQueue(queueName, ex))
                            .onItem().transformToMulti(v -> establishBindings(client, ic))
                            .onItem().ignoreAsUni().onItem().transform(v -> queueName);
                });
    }

    /**
     * Returns a stream that will create bindings from the queue to the exchange with each of the
     * supplied routing keys.
     *
     * @param client the {@link RabbitMQClient} to use
     * @param ic the incoming channel configuration
     * @return a stream of routing keys
     */
    private Multi<String> establishBindings(
            final RabbitMQClient client,
            final RabbitMQConnectorIncomingConfiguration ic) {
        final String exchangeName = getExchangeName(ic);
        final String queueName = ic.getQueueName();
        final List<String> routingKeys = Arrays.stream(ic.getRoutingKeys().split(","))
                .map(String::trim).collect(Collectors.toList());
        final Map<String, Object> arguments = parseArguments(ic.getArguments());

        // Skip queue bindings if exchange name is default ("")
        if (exchangeName.isEmpty()) {
            return Multi.createFrom().empty();
        }
        return Multi.createFrom().iterable(routingKeys)
                .onItem().call(routingKey -> client.queueBind(serverQueueName(queueName), exchangeName, routingKey, arguments))
                .onItem()
                .invoke(routingKey -> log.bindingEstablished(queueName, exchangeName, routingKey, arguments.toString()))
                .onFailure().invoke(ex -> log.unableToEstablishBinding(queueName, exchangeName, ex));
    }

    private Map<String, Object> parseArguments(
            final Optional<String> argumentsConfig) {
        Map<String, Object> argumentsBinding = new HashMap<>();
        argumentsConfig.ifPresent(args -> {
            Arrays.stream(args.split(","))
                    .map(String::trim)
                    .forEach(argumentKeyValue -> {
                        String[] argumentKeyValueSplit = argumentKeyValue.split(":");
                        if (argumentKeyValueSplit.length == 2) {
                            String key = argumentKeyValueSplit[0];
                            String value = argumentKeyValueSplit[1];
                            argumentsBinding.put(key, value);
                        }
                    });
        });
        return argumentsBinding;
    }

    private RabbitMQFailureHandler createFailureHandler(RabbitMQConnectorIncomingConfiguration config) {
        String strategy = config.getFailureStrategy();
        Instance<RabbitMQFailureHandler.Factory> failureHandlerFactory = CDIUtils.getInstanceById(failureHandlerFactories,
                strategy);
        if (failureHandlerFactory.isResolvable()) {
            return failureHandlerFactory.get().create(config, this);
        } else {
            throw ex.illegalArgumentInvalidFailureStrategy(strategy);
        }
    }

    private String serverQueueName(String name) {
        if (name.equals("(server.auto)")) {
            return "";
        }
        return name;
    }

    public void reportIncomingFailure(String channel, Throwable reason) {
        log.failureReported(channel, reason);
        incomingChannelStatus.put(channel, ChannelStatus.NOT_CONNECTED);
        Flow.Subscription subscription = subscriptions.remove(channel);
        if (subscription != null) {
            subscription.cancel();
        }
        RabbitMQClient client = clients.remove(channel);
        if (client != null) {
            // Called on vertx context, we can't block: stop clients without waiting
            client.stopAndForget();
        }
    }

    public RabbitMQAckHandler createAckHandler(RabbitMQConnectorIncomingConfiguration ic) {
        return (Boolean.TRUE.equals(ic.getAutoAcknowledgement())) ? new RabbitMQAutoAck(ic.getChannel())
                : new RabbitMQAck(ic.getChannel());
    }
}