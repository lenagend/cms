package com.mingchico.cms.core.xss;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class XssJacksonDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    private boolean isAllowHtml = false;

    public XssJacksonDeserializer() {}

    public XssJacksonDeserializer(boolean isAllowHtml) {
        this.isAllowHtml = isAllowHtml;
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) return null;

        // @AllowHtml이 붙어있다면 원본 반환
        if (isAllowHtml) {
            return value;
        }

        return Jsoup.clean(value, Safelist.none());
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        // 필드에 @AllowHtml 어노테이션이 있는지 확인
        if (property != null && property.getAnnotation(AllowHtml.class) != null) {
            return new XssJacksonDeserializer(true);
        }
        return new XssJacksonDeserializer(false);
    }
}