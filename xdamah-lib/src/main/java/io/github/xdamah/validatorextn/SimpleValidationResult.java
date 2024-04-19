package io.github.xdamah.validatorextn;

public class SimpleValidationResult implements ValidationResult{
	private boolean valid;
	public SimpleValidationResult(boolean valid) {
		super();
		this.valid = valid;
	}
	@Override
	public boolean isValid() {
		
		return valid;
	}

}
