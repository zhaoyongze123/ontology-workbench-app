package com.devin.ontology;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 2025/6/5 16:33.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@EnableRetry
// 业务和 common 模块都位于本包子树，使用 Spring Boot 默认扫描边界即可。
@SpringBootApplication
public class OntologyServerApplication {

    /**
     * 服务入口.
     *
     * @param args 参数信息
     */
    // CHECKSTYLE:OFF
    public static void main(final String[] args) {
        SpringApplication application = new SpringApplication(OntologyServerApplication.class);
        boolean bootstrapApplyRequested = bootstrapApplyRequested(args);
        if (bootstrapApplyRequested) {
            if (!hasProfile(args, "bootstrap") || !hasArgument(args, "spring.main.web-application-type", "none")) {
                throw new IllegalArgumentException(
                        "bootstrap apply 必须同时显式指定 bootstrap profile 和 web-application-type=none");
            }
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext context = application.run(args);
        boolean bootstrapCommandMode = context.getEnvironment().acceptsProfiles(
                org.springframework.core.env.Profiles.of("bootstrap"))
                && "true".equalsIgnoreCase(context.getEnvironment().getProperty("ontology.bootstrap.apply"));
        if (bootstrapCommandMode) {
            context.close();
            return;
        }
        String port = context.getEnvironment().getProperty("server.port");
        String contextPath = context.getEnvironment().getProperty("server.servlet.context-path");
        String info = "Swagger url: http://localhost:" +
                port +
                contextPath +
                "/doc.html";
        log.info(info);
    }

    private static boolean bootstrapApplyRequested(final String[] args) {
        for (String arg : args) {
            if ("--ontology.bootstrap.apply=true".equals(arg)) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(System.getProperty("ontology.bootstrap.apply"))
                || "true".equalsIgnoreCase(System.getenv("ONTOLOGY_BOOTSTRAP_APPLY"));
    }

    private static boolean hasProfile(final String[] args, final String requiredProfile) {
        String profileValue = System.getProperty("spring.profiles.active");
        if (profileValue == null) profileValue = System.getenv("SPRING_PROFILES_ACTIVE");
        for (String arg : args) {
            if (arg.startsWith("--spring.profiles.active=")) {
                profileValue = arg.substring("--spring.profiles.active=".length());
            }
        }
        return profileValue != null && java.util.Arrays.stream(profileValue.split(","))
                .map(String::trim).anyMatch(requiredProfile::equals);
    }

    private static boolean hasArgument(final String[] args, final String key, final String value) {
        String expected = "--" + key + "=" + value;
        for (String arg : args) {
            if (expected.equals(arg)) return true;
        }
        return ("none".equalsIgnoreCase(value)
                && "none".equalsIgnoreCase(System.getenv("SPRING_MAIN_WEB_APPLICATION_TYPE")));
    }
    // CHECKSTYLE:ON
}
