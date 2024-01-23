package io.github.xdamah.codegen;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import io.swagger.codegen.v3.templates.CodegenTemplateLoader;

public class XdamahFqnCodegenTemplateLoader extends CodegenTemplateLoader {
	@Override
    public URL getResource(String location) throws IOException {
        if (this.getCustomTemplateDir() == null) {
        	
        	if(location.equals("/handlebars/JavaSpring/pojo.mustache"))
        	{
        		location="/handlebars/JavaSpring/fqnpojo.mustache";
        	}
            return this.getClass().getResource(location);
        }
        final File file = new File(location);
        if (file.exists()) {
            return file.toURI().toURL();
        }
        if(location.equals("/handlebars/JavaSpring/pojo.mustache"))
    	{
    		location="/handlebars/JavaSpring/fqnpojo.mustache";
    	}
        return this.getClass().getResource(location);
    }
}
