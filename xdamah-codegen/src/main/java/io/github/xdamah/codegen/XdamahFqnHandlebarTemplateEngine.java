package io.github.xdamah.codegen;

import java.io.IOException;
import java.util.Map;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.cache.TemplateCache;
import com.github.jknack.handlebars.io.TemplateLoader;

import io.swagger.codegen.v3.CodegenConfig;
import io.swagger.codegen.v3.templates.CodegenTemplateLoader;
import io.swagger.codegen.v3.templates.HandlebarTemplateEngine;

public class XdamahFqnHandlebarTemplateEngine extends HandlebarTemplateEngine {
	 private CodegenConfig config;

	public XdamahFqnHandlebarTemplateEngine(CodegenConfig config) {
		super(config);
		this.config=config;//same object as was used in base class
		
	}

	@Override
	public String getRendered(String templateFile, Map<String, Object> templateData) throws IOException {
		
		 final com.github.jknack.handlebars.Template hTemplate = getHandlebars(templateFile);
	        return hTemplate.apply(templateData);
	}
	 private com.github.jknack.handlebars.Template getHandlebars(String templateFile) throws IOException {
	        templateFile = templateFile.replace("\\", "/");
	        
	        final String templateDir = config.templateDir().replace("\\", "/");
	      
	        final TemplateLoader templateLoader;
	        String customTemplateDir = config.customTemplateDir() != null ? config.customTemplateDir().replace("\\", "/") : null;
	        templateLoader = new XdamahFqnCodegenTemplateLoader()
	                .templateDir(templateDir)
	                .customTemplateDir(customTemplateDir);
	        Handlebars handlebars = new Handlebars(templateLoader);
	        handlebars.prettyPrint(true);
	      
	        config.addHandlebarHelpers(handlebars);
	        TemplateCache cache = handlebars.getCache();
	        //System.out.println("cache="+cache.getClass().getName());
	        return handlebars.compile(templateFile);
	    }
}
