package com.amazonaws.lambda.thumbnail;

public class ResponseModel {

	String message;
	Object data;

	public ResponseModel(String message, Object data) {
		super();
		this.message = message;
		this.data = data;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
}
