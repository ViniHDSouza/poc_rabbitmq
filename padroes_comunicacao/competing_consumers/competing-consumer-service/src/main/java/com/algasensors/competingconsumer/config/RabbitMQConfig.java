package com.algasensors.competingconsumer.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /**
     * A fila já foi declarada pelo Sender.
     * O competing-consumer-service só precisa do converter
     * para desserializar as mensagens.
     *
     * O pool de workers (concurrency/prefetch/ack-mode)
     * é inteiramente configurado no application.yml.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
