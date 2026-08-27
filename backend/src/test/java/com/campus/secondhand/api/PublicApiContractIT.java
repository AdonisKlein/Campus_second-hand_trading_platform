package com.campus.secondhand.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@ActiveProfiles("test")
class PublicApiContractIT {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void controllerMappingsMatchFrozenPublicContract() throws Exception {
        Set<String> expected = new TreeSet<>();
        Path contract = Path.of(System.getProperty("user.dir"))
            .resolve("../contracts/http/public-api-v1.tsv")
            .normalize();
        for (String line : Files.readAllLines(contract)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\\t");
            expected.add(columns[0] + " " + columns[1]);
        }

        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!isApplicationController(handler)) {
                return;
            }
            mapping.getMethodsCondition().getMethods().forEach(method ->
                mapping.getPatternValues().forEach(pattern ->
                    actual.add(method.name() + " /api" + pattern)));
        });

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private boolean isApplicationController(HandlerMethod handler) {
        Package controllerPackage = handler.getBeanType().getPackage();
        return controllerPackage != null
            && controllerPackage.getName().startsWith("com.campus.secondhand");
    }
}
