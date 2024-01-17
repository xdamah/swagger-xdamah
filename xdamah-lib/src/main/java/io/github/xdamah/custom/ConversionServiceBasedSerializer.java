package io.github.xdamah.custom;

import java.io.IOException;

import org.springframework.core.convert.ConversionService;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class ConversionServiceBasedSerializer<E> extends StdSerializer<E> {
	private ConversionService conversionService;

	public ConversionServiceBasedSerializer(Class<E> t, ConversionService conversionService) {
		super(t);
		this.conversionService = conversionService;
	}

	@Override
	public void serialize(E value, JsonGenerator gen, SerializerProvider provider) throws IOException {

		gen.writeString(conversionService.convert(value, String.class));
	}

}
