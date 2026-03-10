package com.algasensors.consumer.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do RabbitMQ no Consumer.
 *
 * O Consumer NÃO redeclara a queue, exchange nem o binding — essa
 * responsabilidade é do Producer. Aqui registramos apenas o conversor
 * JSON para que o @RabbitListener consiga desserializar o payload
 * recebido como PessoaDTO automaticamente.
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Mesmo conversor usado no Producer.
     * O Spring AMQP usa o header "__TypeId__" gravado na mensagem
     * para saber qual classe instanciar na desserialização.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
