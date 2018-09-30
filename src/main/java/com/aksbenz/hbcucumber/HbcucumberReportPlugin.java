package com.aksbenz.hbcucumber;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

@Mojo(name="reporting")
public class HbcucumberReportPlugin extends AbstractMojo
{
	/**
     * The path to the Cucumber JSON files.
     */
    @Parameter(property = "reporting.sourceJsonReportDirectory", required = true)
    private String sourceJsonReportDirectory = "";

    /**
     * The location of the generated report.
     */
    @Parameter(property = "reporting.generatedHtmlReportDirectory", required = true)
    private String generatedHtmlReportDirectory = "";

    /**
     * Handlebar Template File
     */
    @Parameter(property = "reporting.sourceTemplateFile", required = true)
    private String sourceTemplateFile = "";
    
    /**
     * Template Id to read from an HTML file.
     * 
     * This is useful if the Template File is a complex html as a SPA
     * and should not be treated wholly as a Handlebar Template file
     * 
     * Example:
     * reporting.sourceTemplateId = "entry-template"
     * 
     * Template file is an html with following:
     * <script id="entry-template" type="text/x-handlebars-template">
     * <div class="features">
     * 	{{#each this}}
     * 		<div>Feature: {{name}}</div>
     * 	{{/each}}
     * </div>
     * </script> 
     * 
     * Will read the HTML and extract the INNER HTML content of the script tag with id="entry-template"
     * and use that as the HBS Template. 
     * 
     * Will use the whole Template file to create the REPORT. 
     * Removes the SCRIPT tag above and replaces with REPORT HTML.
     */
    @Parameter(property = "reporting.sourceTemplateId")
    private String sourceTemplateId;
    
    /**
     * Whether to throw error for unrecognized helpers in template
     */
    @Parameter(property = "reporting.throwExceptionUnrecognizedHelper", defaultValue = "false")
    
    private Boolean throwExceptionUnrecognizedHelper;    
    private Log logger = getLog();
    private Handlebars hb;
    
    public void execute() throws MojoExecutionException
    {   
    	// Validate Input parameters
    	logger.info(sourceJsonReportDirectory);
    	
    	File templateFile = new File(sourceTemplateFile);
    	File[] reportFiles = new File(sourceJsonReportDirectory).listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
    	
    	if(!templateFile.exists() || templateFile.isDirectory()) { 
    	    throw new MojoExecutionException("Template File Not Found: " + sourceTemplateFile);
    	}
    	logger.info("Template File Exists");
    	
    	// Fetch the reports data
    	logger.info("Reading JSON Report files");
    	String reportData;
  		try {
    		reportData = mergeJsonReports(reportFiles);    		
		} catch (IOException e) {
			e.printStackTrace();
			throw new MojoExecutionException("Error in reading JSON Result File: " + sourceJsonReportDirectory + " Exception: " + e.toString());
		}

    	setHandlebars(templateFile);
  		    	
    	/** Register Helpers
    	 * 	Register All Conditional Helpers: https://github.com/jknack/handlebars.java/blob/master/handlebars/src/main/java/com/github/jknack/handlebars/helper/ConditionalHelpers.java
    	 *  Register All String Helpers: https://github.com/jknack/handlebars.java/blob/master/handlebars/src/main/java/com/github/jknack/handlebars/helper/StringHelpers.java
    	 *  Register Custom Helpers
    	 *  Disable/Enable Exception for unrecognized helper in template
    	 */
    	
    	CustomHelpers.register(hb);
    	StringHelpers.register(hb);
		ConditionalHelpers[] helpers = ConditionalHelpers.values();		
	    for (ConditionalHelpers helper : helpers) {
	    	hb.registerHelper(helper.name(), helper);
	    }
	    
	    //Override Missing Helper to not throw exception
	    if (throwExceptionUnrecognizedHelper == false) {
	    	hb.registerHelperMissing(new Helper<Object>() {
		    	@Override
		        public CharSequence apply(final Object context, final Options options) throws IOException {
		    	  logger.error("UNRECOGNIZED HELPER IN TEMPLATE: " + options.fn.text());
		          return options.fn.text();
		        }
		      });
	    }
	    
  		/* End Register Helpers */
	    
  		// Convert JSON to a Collection which can be fed to Handlebars Context
    	Gson gson = new Gson();
		Type type = new TypeToken<Collection<Object>>(){}.getType();
		Collection<Object> map = gson.fromJson(reportData, type);   
		
		logger.info("Data converted to GSON");
		Context context = Context.newBuilder(map).build();
		logger.info("Context set");
		
		Template template;
		
		try {
			template = getTemplate(templateFile);
			logger.info("Template compiled");
			
			// Generate HTML report
			String report = template.apply(context);
			logger.info("Template converted to report");			
			writeReport(report, templateFile);
		} catch (IOException e) {
			throw new MojoExecutionException("Error in creating report: " + generatedHtmlReportDirectory + "\\report.html Exception: " + e.getMessage());
		}
    	    	
    }

    // Merge multiple json report files into a single json oject
    // Each json file is an array of objects. Add all objects into a single array
    // Create a top level object with single property "features" = array of objects
    public String mergeJsonReports(File[] dataFiles) throws IOException {
    	logger.info("Start Merge");
    	JsonArray allFeatures = new JsonArray();
    	    	
    	for(File dataFile: dataFiles) {
    		logger.info("Reading Report File: " + dataFile.getName());
    		String reportData = Files.lines(Paths.get(dataFile.toURI())).collect(Collectors.joining());
    		JsonElement jsElem = new JsonParser().parse(reportData);
    		
    		// If root level is not an array, then file is not proper, ignore it
    		if (jsElem.isJsonArray()) {
    			JsonArray features = jsElem.getAsJsonArray();
    			features.forEach((jsEl) -> allFeatures.add(jsEl));
    			logger.info("Valid Report File: " + dataFile.getName());
    		}
    		else
    			logger.info("NOT a Valid Report File: " + dataFile.getName());
    	}
    	
    	logger.info("End Merge");
		return new Gson().toJson(allFeatures);
    }
    
    public void setHandlebars(File templateFile) {
    	logger.info("START: setHandlebars");
    	if (sourceTemplateId == null) {
    		logger.info("Handlebars using File Template Loader");
	    	TemplateLoader tl = new FileTemplateLoader(templateFile.getParent());
	    	tl.setSuffix("");    	  		
	    	hb = new Handlebars(tl);
    	}
    	else {
    		logger.info("Handlebars using Template ID");
    		hb = new Handlebars();
    	}
    	logger.info("END: setHandlebars");
    }
    
    public Template getTemplate(File templateFile) throws MojoExecutionException {
    	Template template;
    	
    	try {
    		if (sourceTemplateId == null) {
    			template = hb.compile(templateFile.getName());
    		}
    		else {
    			String tempateStr = getHbsFromHtml(templateFile, sourceTemplateId);
    			if (tempateStr == null)
    				throw new MojoExecutionException("Unable to find TAG with ID=" + sourceTemplateId + " in template file: " + sourceTemplateFile);
    			template = hb.compileInline(tempateStr);
    		}    		
    	}catch (IOException e) {
			e.printStackTrace();
			throw new MojoExecutionException("Unable to load template file");
		}
    	return template;
    }
    
    public String getHbsFromHtml(File htmlFile, String id) throws IOException {
    	logger.info("START: getHbsFromHtml");
    	
    	Document doc = Jsoup.parse(htmlFile, "UTF-8");
    	Element hbsElement = doc.selectFirst(id.startsWith("#") ? id : "#" + id);
    	
    	logger.info("getHbsFromHtml: " + (hbsElement == null ? "HTML Element not found with ID=" + id : "hbsElement found"));
    	logger.info("END: getHbsFromHtml");
    	return hbsElement == null ? null : hbsElement.html();
    }
    
    public void writeReport(String report, File templateFile) throws IOException {
    	new File(generatedHtmlReportDirectory).mkdirs();
		BufferedWriter writer = new BufferedWriter(new FileWriter(generatedHtmlReportDirectory + "\\report.html"));
		logger.info("Report file bufferedWriter created");
		
    	if (sourceTemplateId == null) {
		    writer.write(report);
    	}
    	else {
    		Document doc = Jsoup.parse(templateFile, "UTF-8");
        	Element hbsElement = doc.selectFirst(sourceTemplateId.startsWith("#") ? sourceTemplateId : "#" + sourceTemplateId);
        	hbsElement.wrap(report);
        	hbsElement.remove();
        	writer.write(doc.outerHtml());
    	}
    	
    	writer.close();
	    logger.info("Report written to file: " );
    }
}