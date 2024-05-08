package io.github.xdamah.validatorextn;

import java.util.HashMap;

import org.springframework.stereotype.Component;



import io.github.xdamah.validatorextn.BaseValidatorExtension;
import io.github.xdamah.validatorextn.IValidator;
import io.github.xdamah.validatorextn.XdamahCardValidator;

//this is just a democustome validator
//its verysimple only for demo purposes
//can be enhanced later for  non string types and container nodes- array node, object node
//when its base class is taking care of all  extension validation requirements can shift the base class to the core library


public class DoNothingValidatorExtension extends BaseValidatorExtension {

	@Override
	public void onInitRegisterCustomSchemas() {
		
		
	}

	@Override
	protected String[] watchedExtensions() {
		
		return null;
	}

	@Override
	protected HashMap<String, IValidator> mapValidators() {
		
		return null;
	}

}
