package com.fan.lazyday.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    private final SpringTemplateEngine templateEngine;

    public String render(String templateName, Map<String, Object> model) {
        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        if (model != null) {
            context.setVariables(model);
        }
        return templateEngine.process("email/" + templateName, context);
    }
}
