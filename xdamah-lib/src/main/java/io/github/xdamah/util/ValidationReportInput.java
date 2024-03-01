package io.github.xdamah.util;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import com.atlassian.oai.validator.model.Body;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.MessageContext;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;

public class ValidationReportInput {
	
	
	public ValidationReportInput(RequestBody apiRequestBodyDefinition, MessageContext context,
			Optional<Pair<String, MediaType>> maybeApiMediaTypeForRequest, String contentType, 
			Optional<Body> requestBody) {
		super();
		this.apiRequestBodyDefinition = apiRequestBodyDefinition;
		this.context = context;
		this.maybeApiMediaTypeForRequest = maybeApiMediaTypeForRequest;
		this.contentType = contentType;
		this.requestBody = requestBody;
	}
	private final RequestBody apiRequestBodyDefinition;
	private final ValidationReport.MessageContext context;
	private final Optional<Pair<String, MediaType>> maybeApiMediaTypeForRequest;
	private final String contentType;
	private final Optional<Body> requestBody;
	public RequestBody getApiRequestBodyDefinition() {
		return apiRequestBodyDefinition;
	}
	public ValidationReport.MessageContext getContext() {
		return context;
	}
	public Optional<Pair<String, MediaType>> getMaybeApiMediaTypeForRequest() {
		return maybeApiMediaTypeForRequest;
	}
	public String getContentType() {
		return contentType;
	}
	public Optional<Body> getRequestBody() {
		return requestBody;
	}

}
