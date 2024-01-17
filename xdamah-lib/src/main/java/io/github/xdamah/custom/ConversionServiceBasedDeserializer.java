package io.github.xdamah.custom;

import java.io.IOException;

import org.springframework.core.convert.ConversionService;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class ConversionServiceBasedDeserializer<E> extends StdDeserializer<E> {
	private ConversionService conversionService;
	private Class<E> vc;

	public ConversionServiceBasedDeserializer(final Class<E> vc, ConversionService conversionService) {
		super(vc);
		this.conversionService = conversionService;
		this.vc = vc;
	}

	@Override
	public E deserialize(final JsonParser jp, final DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String readValueAs = jp.readValueAs(String.class);
		return this.conversionService.convert(readValueAs, this.vc);
	}

}