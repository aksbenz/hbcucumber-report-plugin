package com.aksbenz.hbcucumber;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.Validate.notNull;
import static org.apache.commons.lang3.Validate.validIndex;

import org.codehaus.plexus.util.Base64;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public enum CustomHelpers implements Helper<Object> {

	
	/**
	 * @author aksbenz
	 * 
	 * Cleans the HTML ID attribute
	 * Removes all characters except a-z, A-Z, 0-9, - and _
	 * 
	 * Example:
	 * idvalue = "abcd.hello&value_2"
	 * 
	 * {{cleanid idvalue}} --> abcdhellovalue_2
	 */
	cleanid {
		@Override
		public Object apply(Object value, Options options) {
			if (value == null)
				return "";
			else
				return ((String)value).replaceAll("[^a-zA-Z0-9-_]*", "");
	    }
	},
	
	/**
	 * @author aksbenz
	 * 
	 * Cucumber JSON Reporter stores Nanoseconds value in duration field 
	 * Inputs Nanoseconds value and returns X - seconds Y - millisecond
	 *
	 * Example
	 * 
	 * dur = 900953679
	 * {{duration dur}} --> 0s 900ms
	 */
	duration {
		public Object apply(Object value, Options options) {
			String duration = "";
			
			if (value == null)
				duration = "0s 0ms";
			else {
				Duration d = Duration.ofNanos(Math.round((Double)value));
				duration = d.toSeconds() + "s " + d.toMillisPart() + "ms";
			}
	    	return duration;
	    }
	},
	
	/**
	 * @author aksbenz
	 * 
	 * Math helper
	 *
	 * Example
	 * 
	 * dur = 900953679
	 * {{duration dur}} --> 0s 900ms
	 */
	math {
		public Object apply(Object value, Options options) {
			if (value == null)
				return "NaN";
			
			validIndex(options.params, 1, "Math helper expects 3 inputs, left value, operator and right value");
			Double lValue = Double.valueOf(value.toString());
			String op = (String)options.param(0);
			Double rValue = Double.valueOf(options.param(1).toString());
			Double result;
			
			switch(op.toLowerCase()) {
			case "+": 
				result = lValue + rValue;
				break;
			case "-":
				result = lValue - rValue;
				break;
			case "/":
				result = lValue/rValue;
				break;
			case "*":
				result = lValue * rValue;
				break;
			case "%":
				result = lValue%rValue;
				break;
			case "^":
				result = Math.pow(lValue, rValue);
				break;
			case "max":
				result = Math.max(lValue, rValue);
				break;
			case "min":
				result = Math.min(lValue, rValue);
				break;
			default:
				result = lValue;
			}
			
			// Check if Whole number then return value without decimal			
			String retValue = (result % 1 == 0) ? String.valueOf(Math.round(result)) : result.toString();
			return retValue;
	    }
	},
	
	/**
	 * @author aksbenz
	 * 
	 * Returns the HTML to be directly embedded in reports for embedding type entries in JSON report
	 * Use triple braces in Template to directly embed returned HTML in report
	 * 
	 * For Mime Type of Images, returns IMG tag with base64 encoded values.
	 * For every other Mime Type returns SPAN tag with text.
	 * If no Mime Type provided returns SPAN tag with text.
	 * 
	 * Example:
	 * 
	 * embedData = "eyJzb3VyY2UiOiJWTVAiLCJ1dWlkIjoiYWE3YjMwY2EtZTNiYy00ODUzLWFjY2"
	 * mimeType = "image/jpeg"
	 * {{{embedmime embedData mimeType}}} --> <img src="data:image/jpeg;base64 eyJzb3VyY2UiOiJWTVAiLCJ1dWlkIjoiYWE3YjMwY2EtZTNiYy00ODUzLWFjY2" alt="Embedded Image" />
	 * 
	 * 
	 * embedData = "SGVsbG8gVGhlcmU="
	 * mimeType = "text/plain"
	 * {{{embedmime embedData mimeType}}} --> <span>Hello There</span>
	 * {{{embedmime embedData}}} --> <span>Hello There</span>
	 *
	 */
	embedmime {
		public Object apply(Object value, Options options) {		    	
			if (value == null)
				return "";
			
	    	String htmlstr = "";
	    	String str = (String)value;
	    	String mimeType = (options.params.length > 0) ? (String)options.param(0) : "unknown";		    			    	
	    	
	    	if (isImage(mimeType)) {
	    		htmlstr = "<img src=\"data:"+ mimeType +";base64, "+ str +"\" alt=\"Embedded Image\" />";
	    	}
	    	else if (mimeType.equalsIgnoreCase("application/json")) {
	    		byte[] b = Base64.decodeBase64(str.getBytes(StandardCharsets.UTF_8));
	    		String decoded = new String(b);
	    		JsonParser parser = new JsonParser();
	    		JsonElement json = parser.parse(decoded);
	    		Gson gson = new GsonBuilder().setPrettyPrinting().create();
	    		String jsonStr = gson.toJson(json);
	    		htmlstr = "<pre>" + jsonStr + "</pre>";
	    	}
	    	else {
	    		byte[] b = Base64.decodeBase64(str.getBytes(StandardCharsets.UTF_8));
	    		htmlstr = "<pre>" + new String(b) + "</pre>";
	    	}
	    	
	    	return htmlstr;
	    }
	};
	
	/**
	 * Register the helper in a handlebars instance.
	 *
	 * @param handlebars A handlebars object. Required.
	 */
	public void registerHelper(final Handlebars handlebars) {
	  notNull(handlebars, "The handlebars is required.");
	  handlebars.registerHelper(name(), this);
	}
	
	/**
	 * Register all the helpers.
	 *
	 * @param handlebars The helper's owner. Required.
	 */
	public static void register(final Handlebars handlebars) {
	  notNull(handlebars, "A handlebars object is required.");
	  CustomHelpers[] helpers = values();
	  for (CustomHelpers helper : helpers) {
	    helper.registerHelper(handlebars);
	  }
	}
	
	protected boolean isImage(String mimeType) {
		return
	        mimeType.equalsIgnoreCase("image/png") ||
	        mimeType.equalsIgnoreCase("image/jpeg") ||
	        mimeType.equalsIgnoreCase("image/gif") ||
	        mimeType.equalsIgnoreCase("image/svg") ||
	        mimeType.equalsIgnoreCase("image/svg+xml");
	}
}
