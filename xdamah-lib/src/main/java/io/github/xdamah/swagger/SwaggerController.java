package io.github.xdamah.swagger;

import java.net.MalformedURLException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerController {
	
	

	private byte[] modifiedJson;

	public void setModifiedJson(byte[] modifiedJson) {
		this.modifiedJson = modifiedJson;
	}

	@RequestMapping(path = "/api-docs/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ByteArrayResource get() throws MalformedURLException {

		return new ByteArrayResource(modifiedJson);
	}

	@RequestMapping(path = "/api-docs/swagger-config", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ClassPathResource get1() {

		return new ClassPathResource("swagger-config.json");
	}

}
