package io.github.xdamah.modelconverter;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JavaType;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.ByteArraySchema;

public class ByteArrayPropertyConverter implements ModelConverter {

	@Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (type.isSchemaProperty()) {
            JavaType _type = Json.mapper().constructType(type.getType());
            if (_type != null) {
                Class<?> cls = _type.getRawClass();
                boolean classIsArray = cls.isArray();
                Class<?> componentType = cls.componentType();
                if(classIsArray && componentType==byte.class)
                {
                	return new ByteArraySchema();
                }
//                if (MyCustomClass.class.isAssignableFrom(cls)) {
//                    return new DateTimeSchema();
//                }
            }
        }
        if (chain.hasNext()) {
            return chain.next().resolve(type, context, chain);
        } else {
            return null;
        }
    }
    
}