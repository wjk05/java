package com.ruici.ai.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ruici AI Platform API")
                        .description("泛职业文档分析、多场景情景模拟、知识库与语音交互 RESTful API 文档")
                        .version("1.0.0"));
    }
}
