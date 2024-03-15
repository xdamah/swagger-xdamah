package io.github.xdamah.advice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Level;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.atlassian.oai.validator.report.ValidationReportFormat;
import com.atlassian.oai.validator.springmvc.InvalidRequestException;

@ControllerAdvice()
@Order(Ordered.LOWEST_PRECEDENCE)
public class SwaggerValidationExceptionHandler {//extends ResponseEntityExceptionHandler 

	private static final Logger logger = LoggerFactory.getLogger(SwaggerValidationExceptionHandler.class);

	private final ValidationReportFormat validationReportFormat = JsonValidationReportFormat.getInstance();

	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<String> handle(final InvalidRequestException invalidRequestException) {

		ValidationReport validationReport = invalidRequestException.getValidationReport();
		List<Message> messages = validationReport.getMessages();

		List<Message> filteredMessages = new ArrayList<>();
		for (Message message : messages) {
			Level level = message.getLevel();
			if (level == Level.ERROR) {
				filteredMessages.add(message);
			}
		}
		validationReport = ValidationReport.from(filteredMessages);

		String json = validationReportFormat.apply(validationReport);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> responseEntity = new ResponseEntity<>(json, headers, HttpStatus.BAD_REQUEST);

		return responseEntity;
	}

	@ExceptionHandler(Exception.class)

	public ResponseEntity<Map<String, String>> handle(final Exception e) {
		Map<String, String> map = new HashMap<>();

		UUID uuid = UUID.randomUUID();
		String logRef = uuid.toString();
		String msg = "Unexpected problem happened. Note refID=" + logRef;

		logger.error(msg, e);

		map.put("problem", msg);
		return new ResponseEntity<>(map, HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
