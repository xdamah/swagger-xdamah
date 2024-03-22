package io.github.xdamah.modelconverter;

import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;

public class SubTypedPropertyConverter implements ModelConverter {

	@Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (type.isSchemaProperty()) {
            JavaType _type = Json.mapper().constructType(type.getType());
            if (_type != null) {
            	if(_type instanceof CollectionType)
            	{
            		CollectionType ct=(CollectionType) _type;
            		JavaType contentType = ct.getContentType();
            		if(contentType instanceof SimpleType)
            		{
            			SimpleType st=(SimpleType) contentType;
            			Class<?> rawClass = st.getRawClass();
            			String dsicrimnatorPropertyName=null;
            			
            			if(rawClass.isAnnotationPresent(JsonTypeInfo.class))
            			{
            				JsonTypeInfo jsonTypeInfo= rawClass.getAnnotation(JsonTypeInfo.class);
                        	Id use = jsonTypeInfo.use();
                        	
                        	String property = jsonTypeInfo.property();
                        	
                        	dsicrimnatorPropertyName=property;
                        	boolean visible = jsonTypeInfo.visible();
                        	As include = jsonTypeInfo.include();
                        	
                        	
            			}
            			if(rawClass.isAnnotationPresent(JsonSubTypes.class))
            			{
            				ArraySchema arraySchema= new ArraySchema();
            				
            				
            				JsonSubTypes jsonSubTypes= rawClass.getAnnotation(JsonSubTypes.class);
            				Type[] value = jsonSubTypes.value();
            				Schema oneOfSchemaItem = new Schema();
            				arraySchema.setItems(oneOfSchemaItem);
            				Discriminator discriminator = new Discriminator();
            				discriminator.setPropertyName(dsicrimnatorPropertyName);
            				oneOfSchemaItem.setDiscriminator(discriminator);
            				for (Type type2 : value) {
            					Class<?> value2 = type2.value();
            					String name = type2.name();
            					ResolvedSchema resolveAsResolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(value2));
            					Schema schema = resolveAsResolvedSchema.schema;
            					resolveAsResolvedSchema.referencedSchemas.entrySet();
            					String refToAdd=null;
            					for (Entry<String, Schema> entry: resolveAsResolvedSchema.referencedSchemas.entrySet()) {
									if(entry.getValue()==schema)
									{
										refToAdd=entry.getKey();
									}
								}
            					
            				
            					oneOfSchemaItem.addOneOfItem(new Schema().$ref("#/components/schemas/"+refToAdd));
            					
            					
            					
							}
            				return arraySchema;
            			}
            			
            		}
            		
            		
            	}
               

            }
        }
        if (chain.hasNext()) {
            return chain.next().resolve(type, context, chain);
        } else {
            return null;
        }
    }
    
}