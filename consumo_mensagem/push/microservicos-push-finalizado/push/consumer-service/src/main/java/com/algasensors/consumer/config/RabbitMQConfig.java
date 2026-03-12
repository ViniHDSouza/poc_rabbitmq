package com.algasensors.consumer.config;

import com.algasensors.consumer.dto.PessoaDTO;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${spring.rabbitmq.listener.simple.prefetch}")
    private int prefetch;

    @Value("${spring.rabbitmq.listener.simple.concurrency}")
    private int concurrency;

    @Value("${spring.rabbitmq.listener.simple.max-concurrency}")
    private int maxConcurrency;

    // ----------------------------------------------------------------
    // Exchange, fila e binding declarados pelo Consumer.
    //
    // Por que o Consumer também declara?
    //   O @RabbitListener executa queueDeclarePassive ao iniciar —
    //   apenas verifica se a fila existe. Se a fila não existir (porque
    //   o Sender ainda não subiu, ou o broker foi reiniciado), o Spring
    //   lança DeclarationException e o listener não sobe.
    //
    //   Declarar a fila aqui garante que o Consumer cria a estrutura
    //   necessária independentemente da ordem de inicialização dos
    //   serviços. Se a fila já existir com os mesmos atributos, o
    //   broker simplesmente ignora a redeclaração (idempotente).
    // ----------------------------------------------------------------
    @Bean
    public DirectExchange pessoaExchange() {
        return ExchangeBuilder.directExchange(exchange).durable(true).build();
    }

    @Bean
    public Queue pessoaQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding pessoaBinding(Queue pessoaQueue, DirectExchange pessoaExchange) {
        return BindingBuilder.bind(pessoaQueue).to(pessoaExchange).with(routingKey);
    }

    // ----------------------------------------------------------------
    // Converter com mapeamento de tipo por nome lógico.
    //
    // Problema sem o mapeamento:
    //   O Sender grava __TypeId__ = "com.algasensors.sender.dto.PessoaDTO".
    //   O Consumer tenta carregar essa classe — que não existe no seu
    //   classpath — e lança ClassNotFoundException.
    //
    // Solução — nome lógico compartilhado:
    //   Sender  escreve  __TypeId__ = "pessoa"
    //   Consumer lê      "pessoa"  → PessoaDTO (classe local)
    // ----------------------------------------------------------------
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of("pessoa", PessoaDTO.class));
        typeMapper.setTrustedPackages("*");

        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    // ----------------------------------------------------------------
    // SimpleRabbitListenerContainerFactory — a fábrica que configura
    // todos os aspectos do modo PUSH.
    //
    // Ao registrar este bean, o Spring AMQP:
    //   1. Abre uma conexão TCP persistente com o broker
    //   2. Cria um canal AMQP dedicado ao listener
    //   3. Emite basic.consume — registra o consumer no broker
    //   4. O broker passa a entregar mensagens proativamente (PUSH)
    //      sem que o consumer precise pedir
    // ----------------------------------------------------------------
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(prefetch);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);

        return factory;
    }
}
