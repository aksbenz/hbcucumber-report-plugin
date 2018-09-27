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
     * Whether to throw error for unrecognized helpers in template
     */
    @Parameter(property = "reporting.throwExceptionUnrecognizedHelper", defaultValue = "false")
    private Boolean throwExceptionUnrecognizedHelper;
    
    private Log logger = getLog();
    public void execute() throws MojoExecutionException
    {   
    	logger.info(sourceJsonReportDirectory);
    	
    	File templateFile = new File(sourceTemplateFile);
    	if(!templateFile.exists() || templateFile.isDirectory()) { 
    	    throw new MojoExecutionException("Template File Not Found: " + sourceTemplateFile);
    	}
    	logger.info("Template File Exists");
    	
    	File[] dataFiles = new File(sourceJsonReportDirectory).listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
    	
    	logger.info("JSON Report files");
    	logger.info(Integer.toString(dataFiles.length));
    	for(File dataFile:dataFiles) {
    		logger.info(dataFile.getName());
    	}
    	   	
    	TemplateLoader tl = new FileTemplateLoader(templateFile.getParent());
    	tl.setSuffix(templateFile.getName().endsWith(".hbs") ? "" : "hbs");
    	
    	Handlebars handlebars = new Handlebars(tl);
    	
    	// Register Helpers
    	CustomHelpers.register(handlebars);
    	StringHelpers.register(handlebars);
		ConditionalHelpers[] helpers = ConditionalHelpers.values();		
	    for (ConditionalHelpers helper : helpers) {
	    	handlebars.registerHelper(helper.name(), helper);
	    }
	    
	    //Override Missing Helper to not throw exception
	    if (throwExceptionUnrecognizedHelper == false) {
		    handlebars.registerHelperMissing(new Helper<Object>() {
		    	@Override
		        public CharSequence apply(final Object context, final Options options) throws IOException {
		    	  logger.error("UNRECOGNIZED HELPER IN TEMPLATE: " + options.fn.text());
		          return options.fn.text();
		        }
		      });
	    }
	    
	    // Fetch the report data
    	String reportData;
  		try {
    		reportData = mergeJsonReports(dataFiles);    		
		} catch (IOException e) {
			e.printStackTrace();
			throw new MojoExecutionException("Error in reading JSON Result File: " + sourceJsonReportDirectory + " Exception: " + e.toString());
		}
  		
  		// Convert JSON to HashMap which can be fed to Handlebars Context
    	Gson gson = new Gson();
		Type type = new TypeToken<Collection<Object>>(){}.getType();
		Collection<Object> map = gson.fromJson(reportData, type);   
		
		logger.info("Data converted to GSON");
		Context context = Context.newBuilder(map).build();
		logger.info("Context set");
		
		Template template;
		try {
			template = handlebars.compile(templateFile.getName());
			logger.info("Template compiled");
			
			// Generate HTML report
			String report = template.apply(context);			
			logger.info("Template converted to report");
			
			// Write HTML report to file
			new File(generatedHtmlReportDirectory).mkdirs();
			BufferedWriter writer = new BufferedWriter(new FileWriter(generatedHtmlReportDirectory + "\\report.html"));
			logger.info("Report file bufferedWriter created");
		    writer.write(report);
		    writer.close();
		    logger.info("Report written to file: " );
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
    		String reportData = Files.lines(Paths.get(dataFile.toURI())).collect(Collectors.joining());
    		JsonElement jsElem = new JsonParser().parse(reportData);
    		
    		// If root level is not an array, then file is not proper, ignore it
    		if (jsElem.isJsonArray()) {
    			JsonArray features = jsElem.getAsJsonArray();
    			features.forEach((jsEl) -> allFeatures.add(jsEl));
    		}
    	}
    	
    	logger.info("End Merge");
		return new Gson().toJson(allFeatures);
    }
}
