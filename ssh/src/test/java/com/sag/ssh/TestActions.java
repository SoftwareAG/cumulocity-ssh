package com.sag.ssh;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import c8y.Hardware;
import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class TestActions {
    private SpringTemplateEngine templateEngine;

    @BeforeAll
    void init() {
        this.templateEngine = new SpringTemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateEngine.setTemplateResolver(templateResolver);
    }

    @Test
    public void testActionScriptWithoutBlock() {
        String action = "{\"action\":{\"param1\":true, \"group\":{\"param2\":\"This is param2!\"}}}";
        final Context context = new Context();
        try {
            Map<String, Object> json = new ObjectMapper().readValue(action, Map.class);
            context.setVariables(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        String result = templateEngine.process(
                "echo [(${action.param1})] [(${action.param1 ? action.group.param2 : ''})]",
                context);
        log.info(result);
        assertThat(result, is("echo true This is param2!"));
        ((Map<String, Object>) context.getVariable("action")).put("param1", false);
        result = templateEngine.process(
                "echo [(${action.param1})] [(${action.param1 ? action.group.param2 : ''})]",
                context);
        log.info(result);
        assertThat(result, is("echo false "));
    }

    @Test
    public void testActionScriptWithBlock() {
        String action = "{\"action\":{\"param1\":true, \"group\":{\"param2\":\"This is param2!\"}}}";
        final Context context = new Context();
        try {
            Map<String, Object> json = new ObjectMapper().readValue(action, Map.class);
            context.setVariables(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        String result = templateEngine.process(
                "echo [(${action.param1})] [# th:if='${action.param1}'][(${action.group.param2})][/]",
                context);
        log.info(result);
        assertThat(result, is("echo true This is param2!"));
    }

    @Test
    public void testActionScriptWithDeviceContext() {
        String action = "{\"action\":{\"param1\":true, \"group\":{\"param2\":\"This is param2!\"}}}";
        ManagedObjectRepresentation device = new ManagedObjectRepresentation();
        device.setName("My device");
        Hardware hardware = new Hardware("BCM3823", "123456", "a01234");
        device.set(hardware);

        final Context context = new Context();
        try {
            Map<String, Object> json = new ObjectMapper().readValue(action, Map.class);
            json.put("device", device);
            context.setVariables(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        String result = templateEngine.process(
                "echo [(${device.get('c8y_Hardware').model})] [(${action.param1})] [# th:if='${action.param1}'][(${action.group.param2})][/]",
                context);
        log.info(result);
        assertThat(result, is("echo BCM3823 true This is param2!"));
    }

    @Test
    public void testAccessRootContext() {
        Context context = new Context();
        context.setVariable("test", "coucou");
        String result = templateEngine.process("[(${#vars})]", context);
        log.info(result);
        assertThat(result, result.contains("test=coucou"));
    }

}
