package io.github.xdamah.validatorextn;

import org.hibernate.validator.internal.constraintvalidators.hv.LuhnCheckValidator;

public class XdamahLuhnCheckValidator extends LuhnCheckValidator {

	@Override
	public void initialize(int startIndex, int endIndex, int checkDigitIndex, boolean ignoreNonDigitCharacters) {
		
		super.initialize(startIndex, endIndex, checkDigitIndex, ignoreNonDigitCharacters);
	}

}
