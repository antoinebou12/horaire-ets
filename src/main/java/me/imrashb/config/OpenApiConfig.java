package me.imrashb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI horaireETSOpenAPI() {
        Server server = new Server();
        server.setUrl("/");
        server.setDescription("HoraireETS API Server");

        Contact contact = new Contact();
        contact.setEmail("emmanuel.coulombe.1@ens.etsmtl.ca");
        contact.setName("HoraireETS Team");

        License license = new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("HoraireETS API")
                .version("1.0.0")
                .contact(contact)
                .description("API for managing course schedules and combinations at École de technologie supérieure (ETS). " +
                        "This API provides endpoints to retrieve courses, sessions, schedule combinations, and statistics.")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}
