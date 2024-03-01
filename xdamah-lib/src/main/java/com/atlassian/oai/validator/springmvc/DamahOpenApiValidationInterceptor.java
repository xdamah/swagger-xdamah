package com.atlassian.oai.validator.springmvc;


import static com.atlassian.oai.validator.springmvc.OpenApiValidationFilter.ATTRIBUTE_REQUEST_VALIDATION;
import static com.atlassian.oai.validator.springmvc.OpenApiValidationFilter.ATTRIBUTE_RESPONSE_VALIDATION;
import static org.apache.commons.lang3.ClassUtils.getPackageName;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.UrlPathHelper;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Body;
import com.atlassian.oai.validator.model.ByteArrayBody;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;

import io.github.xdamah.controller.DamahController;
import io.github.xdamah.controller.RequestBodyBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DamahOpenApiValidationInterceptor extends OpenApiValidationInterceptor {
	
	private static final Logger LOG = LoggerFactory.getLogger(DamahOpenApiValidationInterceptor.class);
	private static final String ATTRIBUTE_ALREADY_SET_HEADERS = getPackageName(OpenApiValidationInterceptor.class) + ".alreadySetHeaders";
	private final ValidationReportHandler validationReportHandler;


	

	public DamahOpenApiValidationInterceptor(OpenApiInteractionValidator validator) {
		this(new OpenApiValidationService(validator, new UrlPathHelper()));
	}

	public DamahOpenApiValidationInterceptor(OpenApiValidationService openApiValidationService,
			ValidationReportHandler validationReportHandler) {
		super(openApiValidationService, validationReportHandler);
		this.validationReportHandler=validationReportHandler;
		
	}

	public DamahOpenApiValidationInterceptor(OpenApiValidationService openApiValidationService) {
		
		this(openApiValidationService, new DefaultValidationReportHandler());
		
	}
	
	   private static boolean skipValidationStep(final HttpServletRequest servletRequest, final String attributeName) {
	        return !Boolean.TRUE.equals(servletRequest.getAttribute(attributeName));
	    }
	   private static String buildRequestLoggingKey(final HttpServletRequest servletRequest) {
	        return servletRequest.getMethod() + "#" + servletRequest.getRequestURI();
	    }
	   
	   private void validateRequest(final HttpServletRequest servletRequest, final Supplier<Body> bodySupplier) {
	        final String requestLoggingKey = buildRequestLoggingKey(servletRequest);
	        LOG.debug("OpenAPI request validation: {}", requestLoggingKey);

	        final Request request = openApiValidationService.buildRequest(servletRequest, bodySupplier);
	        final ValidationReport validationReport = openApiValidationService.validateRequest(request);

	        validationReportHandler.handleRequestReport(requestLoggingKey, validationReport);
	    }
	
	 @Override
	    public boolean preHandle(final HttpServletRequest servletRequest,
	                             final HttpServletResponse servletResponse,
	                             final Object handler) throws Exception {
	        if (!skipValidationStep(servletRequest, ATTRIBUTE_RESPONSE_VALIDATION)) {
	            // save already set headers for the upcoming response validation
	            final Map<String, List<String>> alreadySetHeaders = openApiValidationService.resolveHeadersOnResponse(servletResponse);
	            servletRequest.setAttribute(ATTRIBUTE_ALREADY_SET_HEADERS, alreadySetHeaders);
	        }

	        if (skipValidationStep(servletRequest, ATTRIBUTE_REQUEST_VALIDATION)) {
	            LOG.debug("OpenAPI request validation skipped for this request");
	        } else {
	            if (servletRequest instanceof ResettableRequestServletWrapper) {
	            	//xml,json
	            	//we must make the stream rereadable if we must use handleForm
	            	//for later
	            	boolean handled=false;//handleForm(servletRequest, handler);
	            	if(!handled)
	            	{
	            		final InputStream inputStream = servletRequest.getInputStream();
		                final Supplier<Body> bodySupplier = () -> new ResettableInputStreamBody((ResettableRequestServletWrapper.CachingServletInputStream) inputStream);
		                validateRequest(servletRequest, bodySupplier);
		                // reset the request's servlet input stream after reading it on validation
		                ((ResettableRequestServletWrapper) servletRequest).resetInputStream();
	            	}
	                
	            } else if (servletRequest instanceof ContentCachingRequestWrapper) {
	            	//form
	            	boolean handled=handleForm(servletRequest, handler);
	            	if(!handled)
	            	{
	            		//original logic from base class
	            		final Supplier<Body> bodySupplier = () -> new ByteArrayBody(((ContentCachingRequestWrapper) servletRequest).getContentAsByteArray());
	                    validateRequest(servletRequest, bodySupplier);
	            	}
	            } else if (servletRequest instanceof StandardMultipartHttpServletRequest) {
	            	
	            	handleForm(servletRequest, handler);
	            	//since this is new we dont have any alternative original
	            }
	            
	            else {
	                LOG.debug("OpenAPI request validation skipped: unsupported HttpServletRequest type");
	            }
	        }
	        return true;
	    }

	private boolean handleForm(final HttpServletRequest servletRequest, final Object handler)
			throws ClassNotFoundException, IOException, StreamReadException, DatabindException, AssertionError,
			ServletException {
		boolean handled=false;
		String contentType = servletRequest.getContentType();
		if(contentType!=null)
		{
			if (contentType.startsWith(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)) {
				contentType = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
			}
			else if (contentType.startsWith(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
				contentType = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
			}
		}
		
		
		if(handler instanceof HandlerMethod)
		{
			HandlerMethod hm=(HandlerMethod) handler;
			Object handlerMethodBean = hm.getBean();
			if(handlerMethodBean instanceof DamahController)
			{
				handled=true;
			
				DamahController c = (DamahController) handlerMethodBean;
				RequestBodyBuilder requestBodyBuilder = new RequestBodyBuilder(c.getOperation(), 
						c.getModelPackageUtil(), c.getObjectMapper(),
						c.getMappingJackson2XmlHttpMessageConverter(), c.getOpenApi(), 
						c.getConversionService());

				requestBodyBuilder.prepareRequestBodyTargetType(contentType);
				Object reqBody = requestBodyBuilder.buildRequestBody(servletRequest, contentType);
				final Supplier<Body> bodySupplier = () ->new XdamahBody(reqBody, c.getObjectMapper());
					validateRequest(servletRequest, bodySupplier);
			}
			
			
			
		}
		return handled;
	}
	 
	 

	    

}
