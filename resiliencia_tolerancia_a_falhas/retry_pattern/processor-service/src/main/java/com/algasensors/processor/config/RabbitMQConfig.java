package com.algasensors.processor.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.dlx}")
    private String dlx;

    @Value("${rabbitmq.dlq}")
    private String dlq;

    @Value("${spring.rabbitmq.listener.simple.retry.max-attempts}")
    private int maxAttempts;

    @Value("${spring.rabbitmq.listener.simple.retry.initial-interval}")
    private long initialInterval;

    @Value("${spring.rabbitmq.listener.simple.retry.multiplier}")
    private double multiplier;

    @Value("${spring.rabbitmq.listener.simple.retry.max-interval}")
    private long maxInterval;

    // ----------------------------------------------------------------
    // Exchange principal
    // ----------------------------------------------------------------
    @Bean
    public DirectExchange pessoaExchange() {
        return ExchangeBuilder.directExchange(exchange).durable(true).build();
    }

    // ----------------------------------------------------------------
    // Fila principal COM argumentos de dead-letter.
    // Após esgotar os retries, o RejectAndDontRequeueRecoverer envia
    // basicNack(requeue=false) → broker encaminha para pessoa.dlx → pessoa.queue.dlq
    // ----------------------------------------------------------------
    @Bean
    public Queue pessoaQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange",    dlx)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    public Binding pessoaBinding(Queue pessoaQueue, DirectExchange pessoaExchange) {
        return BindingBuilder.bind(pessoaQueue).to(pessoaExchange).with(routingKey);
    }

    // ----------------------------------------------------------------
    // Dead Letter Exchange + Dead Letter Queue
    // ----------------------------------------------------------------
    @Bean
    public DirectExchange pessoaDlx() {
        return ExchangeBuilder.directExchange(dlx).durable(true).build();
    }

    @Bean
    public Queue pessoaDlq() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding pessoaDlqBinding(Queue pessoaDlq, DirectExchange pessoaDlx) {
        return BindingBuilder.bind(pessoaDlq).to(pessoaDlx).with(routingKey);
    }

    // ----------------------------------------------------------------
    // Converter JSON <-> Java
    // ----------------------------------------------------------------
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ----------------------------------------------------------------
    // RetryTemplate — define a política de retentativas e o backoff.
    //
    // SimpleRetryPolicy  → limita o número de tentativas (max-attempts)
    // ExponentialBackOffPolicy → calcula os intervalos de espera:
    //   tentativa 1: initialInterval * multiplier^0  = 1000ms
    //   tentativa 2: initialInterval * multiplier^1  = 2000ms
    //   tentativa 3: initialInterval * multiplier^2  = 4000ms
    //   (limitado por maxInterval)
    // ----------------------------------------------------------------
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxInterval);
        template.setBackOffPolicy(backOff);

        return template;
    }

    // ----------------------------------------------------------------
    // MessageRecoverer — executado quando os retries se esgotam.
    //
    // RejectAndDontRequeueRecoverer:
    //   → lança AmqpRejectAndDontRequeueException
    //   → Spring AMQP envia basicNack(requeue=false)
    //   → broker encaminha para a DLQ (via x-dead-letter-exchange)
    //
    // Outras opções (não usadas aqui, apenas para conhecimento):
    //   ImmediateRequeueMessageRecoverer → basicNack(requeue=true) → loop
    //   RepublishMessageRecoverer        → republica manualmente na DLQ
    // ----------------------------------------------------------------
    @Bean
    public MessageRecoverer messageRecoverer() {
        return new RejectAndDontRequeueRecoverer();
    }

    // ----------------------------------------------------------------
    // RetryOperationsInterceptor — intercepta o método listener e
    // aplica o RetryTemplate transparentemente.
    //
    // É o "elo" entre o Spring AMQP e o Spring Retry:
    //   1. Listener lança exceção
    //   2. Interceptor captura
    //   3. RetryTemplate decide: tentar de novo ou chamar MessageRecoverer
    // ----------------------------------------------------------------
    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate())
                .recoverer(messageRecoverer())
                .build();
    }

    // ----------------------------------------------------------------
    // ContainerFactory com retry interceptor aplicado ao listener.
    // ----------------------------------------------------------------
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }
}
