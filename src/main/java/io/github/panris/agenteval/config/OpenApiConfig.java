package io.github.panris.agenteval.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Agent Eval Lite API")
                .version("1.0.0")
                .description(
                    "轻量级 Java Agent 评测系统 REST API。\n\n" +
                    "## 认证\n" +
                    "本 API 无需认证，直接调用即可。\n\n" +
                    "## 测试用例\n" +
                    "- CRUD 操作、分组管理、三维维度\n" +
                    "- 标签管理、批量导入导出\n" +
                    "- 从需求文档自动生成测试用例\n\n" +
                    "## 评测\n" +
                    "- 同步 / 异步评测、多评分器\n" +
                    "- 报告查看、对比、分享、收藏\n\n" +
                    "## 导出\n" +
                    "- JSON / CSV / Excel / PDF"
                )
                .contact(new Contact()
                    .name("panris")
                    .url("https://github.com/panris/agent-eval-lite"))
                .license(new License()
                    .name("MIT")));
    }
}
