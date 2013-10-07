package org.aksw.sparqlify.admin.web.main;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import org.aksw.sparqlify.validation.LoggerCount;
import org.aksw.sparqlify.web.SparqlifyCliHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * 
 * http://stackoverflow.com/questions/10738816/deploying-a-servlet-
 * programmatically-with-jetty
 * http://stackoverflow.com/questions/3718221/add-resources
 * -to-jetty-programmatically
 * 
 * @author raven
 * 
 * 
 */
public class MainSparqlifyWebManager {
	
	private static final Logger logger = LoggerFactory.getLogger(MainSparqlifyWebManager.class);

	
	private static final Options cliOptions = new Options();

	
	public static void printClassPath() {
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		 
        URL[] urls = ((URLClassLoader)cl).getURLs();
 
        for(URL url: urls){
        	System.out.println(url.getFile());
        }
	}
	
	// Source:
	// http://eclipsesource.com/blogs/2009/10/02/executable-wars-with-jetty/
	public static void main(String[] args) throws Exception {

		//System.setProperty("org.apache.jasper.compiler.disablejsr199", "true");
		
		//printClassPath();
		//if(true) { System.exit(0); }
		LoggerCount loggerCount = new LoggerCount(logger);

//		Class.forName("org.postgresql.Driver");
//		
		cliOptions.addOption("P", "port", true, "");

//		cliOptions.addOption("d", "database", true, "Database name");
//		cliOptions.addOption("u", "username", true, "");
//		cliOptions.addOption("p", "password", true, "");
//		cliOptions.addOption("h", "hostname", true, "");
//
//		
		CommandLineParser cliParser = new GnuParser();
		CommandLine commandLine = cliParser.parse(cliOptions, args);

		//SparqlifyCliHelper.parseDataSource(commandLine, loggerCount);
		Integer port = SparqlifyCliHelper.parseInt(commandLine, "P", false, loggerCount);
		
		port = (port == null) ? 7531 : port;
		
		
		Server server = new Server();
		SocketConnector connector = new SocketConnector();

		// Set some timeout options to make debugging easier.
		connector.setMaxIdleTime(1000 * 60 * 60);
		connector.setSoLingerTime(-1);
		connector.setPort(port);
		server.setConnectors(new Connector[] { connector });

		WebAppContext context = new WebAppContext();
		context.setServer(server);
		context.setContextPath("/");

		ProtectionDomain protectionDomain = MainSparqlifyWebManager.class.getProtectionDomain();
		URL location = protectionDomain.getCodeSource().getLocation();
		String externalForm = location.toExternalForm();
		
		
		// Try to detect whether we are being run from an
		// archive (uber jar / war) or just from compiled classes
		if(externalForm.endsWith("/classes/")) {
			externalForm = "src/main/webapp";
			//externalForm = "target/sparqlify-web-admin/";
		}
		
		
		logger.debug("Loading webAppContext from " + externalForm);
        context.setDescriptor(externalForm + "/WEB-INF/web.xml");
		context.setWar(externalForm);

		server.setHandler(context);
		try {
			server.start();
			System.in.read();
			server.stop();
			server.join();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}