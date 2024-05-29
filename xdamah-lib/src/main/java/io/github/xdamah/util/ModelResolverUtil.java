package io.github.xdamah.util;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;

public class ModelResolverUtil {
	
	public static ModelResolver originalModelResolver() {
		List<ModelConverter> converters = ModelConverters.getInstance().getConverters();

		ModelResolver modelResolver=null;
		
		converters = ModelConverters.getInstance().getConverters();
		
		
		for (ModelConverter modelConverter : converters) {
			
			if(modelConverter instanceof ModelResolver)
			{
				modelResolver=(ModelResolver) modelConverter;
			}
			
		}
		return modelResolver;
	}
	
	public static ObjectMapper objectMapper(ModelResolver modelResolver) {
		ObjectMapper objectMapper=null;
		if(modelResolver!=null)
		{
			objectMapper=modelResolver.objectMapper();
		}
		else
		{
			//
			objectMapper= new ObjectMapper();
		}
		return objectMapper;
	}

}
