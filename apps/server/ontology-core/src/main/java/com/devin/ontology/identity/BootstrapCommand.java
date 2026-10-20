package com.devin.ontology.identity;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 受 profile 与显式开关双重保护的初始化命令入口。 */
@Component
@Profile("bootstrap")
@ConditionalOnProperty(name = "ontology.bootstrap.apply", havingValue = "true")
public class BootstrapCommand implements ApplicationRunner {
    private final BootstrapService bootstrapService;
    private final String inputFile;

    public BootstrapCommand(
            final BootstrapService bootstrapService,
            @Value("${ontology.bootstrap.file:}") final String inputFile,
            final Environment environment) {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("bootstrap"))) {
            throw new IllegalStateException("bootstrap 命令要求 bootstrap profile");
        }
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""))) {
            throw new IllegalStateException("bootstrap 命令要求 spring.main.web-application-type=none");
        }
        this.bootstrapService = bootstrapService;
        this.inputFile = inputFile;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (inputFile.isBlank()) {
            throw new IllegalStateException("必须通过 ontology.bootstrap.file 显式指定输入文件");
        }
        BootstrapService.BootstrapResult result = bootstrapService.apply(Path.of(inputFile));
        System.out.printf("Bootstrap 完成状态：%s，用户数：%d，项目数：%d%n",
                result.applied() ? "已应用" : "已验证为重复输入", result.userCount(), result.projectCount());
    }
}
