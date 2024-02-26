package com.atlassian.oai.validator.springmvc;


import static org.apache.commons.lang3.ClassUtils.getPackageName;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.UrlPathHelper;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Body;
import com.atlassian.oai.validator.model.ByteArrayBody;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter;
import com.atlassian.oai.validator.springmvc.OpenApiValidationInterceptor;
import com.atlassian.oai.validator.springmvc.OpenApiValidationService;
import com.atlassian.oai.validator.springmvc.ResettableInputStreamBody;
import com.atlassian.oai.validator.springmvc.ResettableRequestServletWrapper;
import com.atlassian.oai.validator.springmvc.ValidationReportHandler;
import com.atlassian.oai.validator.springmvc.ResettableRequestServletWrapper.CachingServletInputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import static com.atlassian.oai.validator.springmvc.OpenApiValidationFilter.ATTRIBUTE_REQUEST_VALIDATION;
import static com.atlassian.oai.validator.springmvc.OpenApiValidationFilter.ATTRIBUTE_RESPONSE_VALIDATION;

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
	                final InputStream inputStream = servletRequest.getInputStream();
	                final Supplier<Body> bodySupplier = () -> new ResettableInputStreamBody((ResettableRequestServletWrapper.CachingServletInputStream) inputStream);
	                validateRequest(servletRequest, bodySupplier);
	                // reset the request's servlet input stream after reading it on validation
	                ((ResettableRequestServletWrapper) servletRequest).resetInputStream();
	            } else if (servletRequest instanceof ContentCachingRequestWrapper) {
	            	
	                final Supplier<Body> bodySupplier = () ->{
	                	byte[] contentAsByteArray = ((ContentCachingRequestWrapper) servletRequest).getContentAsByteArray();
	                	System.out.println("check=["+new String(contentAsByteArray)+"]");
	                	ByteArrayBody ret = new ByteArrayBody(contentAsByteArray);
	                	return ret;
	                	};
	                validateRequest(servletRequest, bodySupplier);
	            } else if (servletRequest instanceof StandardMultipartHttpServletRequest) {
	            	
	            	final Supplier<Body> bodySupplier = () -> {
	            		byte[] content;
						try {
							content = ctsm(servletRequest);
							return new ByteArrayBody(content);
						} catch (IOException | ServletException e) {
							throw new RuntimeException("problem", e);
						}
	            		
	            		};
	                validateRequest(servletRequest, bodySupplier);
	            }
	            
	            else {
	                LOG.debug("OpenAPI request validation skipped: unsupported HttpServletRequest type");
	            }
	        }
	        return true;
	    }
	 
	  private byte[] ctsm(HttpServletRequest request) throws IOException, ServletException
	    {
	    	StringBuilder sb= new StringBuilder();
	    	Collection<Part> parts = request.getParts();
			for (Part part : parts) {
				String parameterName = part.getName();
				String contentType = part.getContentType();
				//this is for binary
				if(contentType!=null  && !contentType.startsWith("text/plain"))
				{
					//lets for now ignore them for validations
				}
				else
				{
					String[] parameterValues = request.getParameterValues(parameterName);
					
					for (String parameterValue : parameterValues) {
						sb.append("&"+parameterName+"="+parameterValue);
					}
				}
				
			}
			sb.delete(0, 1);
			String string = sb.toString();
			System.out.println(string);
	    	return string.getBytes();
	    }
	    

}
