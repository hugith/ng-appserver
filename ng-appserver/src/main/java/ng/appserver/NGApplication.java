package ng.appserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ng.appserver.directactions.NGDirectActionRequestHandler;
import ng.appserver.routing.NGRouteTable;
import ng.appserver.templating._NGUtilities;
import ng.appserver.wointegration.NGDefaultLifeBeatThread;
import ng.appserver.wointegration.WOMPRequestHandler;

public class NGApplication {

	private static Logger logger = LoggerFactory.getLogger( NGApplication.class );

	/**
	 * FIXME: This is a global NGApplication object. We don't want a global NGApplication object // Hugi 2021-12-29
	 */
	private static NGApplication _application;

	private NGProperties _properties;

	private NGSessionStore _sessionStore;

	private NGResourceManager _resourceManager;

	/**
	 * In the old WO world, this would have been called "requestHandlers".
	 * Since we want to have more dynamic route resolution, it makes sense to move that to a separate object.
	 */
	private NGRouteTable _routeTable = new NGRouteTable();

	/**
	 * FIXME: Initialization still feels a little weird, while we're moving away from the way it's handled in WOApplication. Look a little more into the flow of application initialization // Hugi 2021-12-29
	 */
	public static void run( final String[] args, final Class<? extends NGApplication> applicationClass ) {
		final long startTime = System.currentTimeMillis();

		NGProperties properties = new NGProperties( args );

		// We need to start out with initializing logging to ensure we're seeing everything the application does during the init phase.
		initLogging( properties.propWOOutputPath() );

		if( properties.isDevelopmentMode() ) {
			logger.info( "========================================" );
			logger.info( "===== Running in development mode! =====" );
			logger.info( "========================================" );
		}
		else {
			logger.info( "=======================================" );
			logger.info( "===== Running in production mode! =====" );
			logger.info( "=======================================" );
		}

		logger.info( "===== Properties =====\n" + properties._propertiesMapAsString() );

		try {
			_application = applicationClass.getDeclaredConstructor().newInstance();

			_application._resourceManager = new NGResourceManager();
			_application._sessionStore = new NGServerSessionStore();
			_application._properties = properties;

			_application._routeTable.map( "/wo/", new NGComponentRequestHandler() );
			_application._routeTable.map( "/wr/", new NGResourceRequestHandler() );
			_application._routeTable.map( "/wd/", new NGResourceRequestHandlerDynamic() );
			_application._routeTable.map( "/wa/", new NGDirectActionRequestHandler() );
			_application._routeTable.map( "/womp/", new WOMPRequestHandler() );

			_application.run();
		}
		catch( final Exception e ) {
			e.printStackTrace();
			System.exit( -1 );
		}

		if( properties.propWOLifebeatEnabled() ) {
			NGDefaultLifeBeatThread.start( _application._properties );
		}

		logger.info( "===== Application started in {} ms at {}", (System.currentTimeMillis() - startTime), LocalDateTime.now() );
	}

	private void run() {
		try {
			createAdaptor().start();
		}
		catch( final Exception e ) {
			throw new RuntimeException( e );
		}
	}

	/**
	 * FIXME: This should eventually return the name of our own adaptor. Using Jetty for now (since it's easier to implement) // Hugi 2021-12-29
	 */
	public String adaptorClassName() {
		return "ng.adaptor.jetty.NGAdaptorJetty";
	}

	private NGAdaptor createAdaptor() {
		try {
			final Class<? extends NGAdaptor> adaptorClass = (Class<? extends NGAdaptor>)Class.forName( adaptorClassName() );
			return adaptorClass.getConstructor().newInstance();
		}
		catch( InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e ) {
			// FIXME: Handle the error
			e.printStackTrace();
			System.exit( -1 );
			return null; // wat?
		}
	}

	public NGProperties properties() {
		return _properties;
	}

	public NGRouteTable routeTable() {
		return _routeTable;
	}

	/**
	 * FIXME: I'm not quite sure what to do about this variable. Belongs here or someplace else?
	 */
	public boolean isDevelopmentMode() {
		return _properties.isDevelopmentMode();
	}

	/**
	 * @return A new instance of [componentClass] in the given [context]
	 *
	 * FIXME: Are components really a part of the basic framework? If so; does component construction really belong in NGApplication // Hugi 2021-12-29
	 */
	public <E extends NGComponent> E pageWithName( final Class<E> componentClass, final NGContext context ) {
		final NGComponentDefinition definition = _componentDefinition( componentClass );

		if( definition == null ) {
			throw new RuntimeException( "No such component definition: " + componentClass );
		}

		final E page = (E)definition.componentInstanceInstanceInContext( componentClass, context );
		page._componentDefinition = definition; // FIXME: Butt ugly to do this here // Hugi 2022-01-16
		return page;
	}

	/**
	 * @return The componentDefinition corresponding to the given WOComponent class.
	 *
	 * FIXME: This is currently extremely simplistic. We need to check for the existence of a definition, add localization etc. // Hugi 2022-01-16
	 */
	private NGComponentDefinition _componentDefinition( Class<? extends NGComponent> componentClass ) {
		return new NGComponentDefinition( componentClass );
	}

	public static NGApplication application() {
		return _application;
	}

	public NGResourceManager resourceManager() {
		return _resourceManager;
	}

	public NGSessionStore sessionStore() {
		return _sessionStore;
	}

	public NGSession restoreSessionWithID( final String sessionID ) {
		return sessionStore().checkoutSessionWithID( sessionID );
	}

	public NGResponse dispatchRequest( final NGRequest request ) {

		logger.info( "Handling URI: " + request.uri() );

		cleanupWOURL( request );

		// FIXME: Handle the case of no default request handler gracefully // Hugi 2021-12-29
		if( request.parsedURI().length() == 0 ) {
			return defaultResponse( request );
		}

		final NGRequestHandler requestHandler = _routeTable.handlerForURL( request.uri() );

		if( requestHandler == null ) {
			return new NGResponse( "No request handler found for uri " + request.uri(), 404 );
		}

		// FIXME: We might want to look into a little more exception handling // Hugi 2022-04-18
		try {
			final NGResponse response = requestHandler.handleRequest( request );

			if( response == null ) {
				throw new NullPointerException( String.format( "'%s' returned a null response. That's just rude.", requestHandler.getClass().getName() ) );
			}

			return response;
		}
		catch( Throwable throwable ) {
			handleException( throwable );
			return exceptionResponse( throwable );
		}
	}

	/**
	 * Handle a Request/Response loop occurring throwable before generating a response for it
	 */
	protected void handleException( Throwable throwable ) {
		throwable.printStackTrace();
	}

	/**
	 * @return The response generated when an exception occurs
	 *
	 * FIXME: We need to allow for different response types for production/development environments // Hugi 2022-04-20
	 */
	public NGResponse exceptionResponse( final Throwable throwable ) {
		final StringBuilder b = new StringBuilder();
		b.append( "<style>body{ font-family: sans-serif}</style>" );
		b.append( String.format( "<h3>An exception occurred</h3>" ) );
		b.append( String.format( "<h1>%s</h1>", throwable.getClass().getName() ) );
		b.append( String.format( "<h2>%s</h2>", throwable.getMessage() ) );

		if( throwable.getCause() != null ) {
			b.append( String.format( "<h3>Cause: %s</h3>", throwable.getCause().getMessage() ) );
		}

		for( StackTraceElement ste : throwable.getStackTrace() ) {
			final String packageNameOnly = ste.getClassName().substring( 0, ste.getClassName().lastIndexOf( "." ) );
			final String simpleClassNameOnly = ste.getClassName().substring( ste.getClassName().lastIndexOf( "." ) + 1 );

			b.append( String.format( "<span style=\"display: inline-block; min-width: 300px\">%s</span>", packageNameOnly ) );
			b.append( String.format( "<span style=\"display: inline-block; min-width: 500px\">%s</span>", simpleClassNameOnly + "." + ste.getMethodName() + "()" ) );
			b.append( ste.getFileName() + ":" + ste.getLineNumber() );
			b.append( "<br>" );
		}

		return new NGResponse( b.toString(), 500 );
	}

	/**
	 * @return A default response for requests to the root.
	 *
	 *  FIXME: This is just here as a temporary placeholder until we decide on a nicer default request handling mechanism
	 */
	public NGResponse defaultResponse( final NGRequest request ) {
		NGResponse response = new NGResponse( "Welcome to NGObjects!\nSorry, but I'm young and I still have no idea how to handle the default request", 404 );
		response.appendContentString( "\n\nWould you like to see your request headers instead?\n\n" );

		for( Entry<String, List<String>> header : request.headers().entrySet() ) {
			response.appendContentString( header.getKey() + " : " + header.getValue() );
			response.appendContentString( "\n" );
		}

		return response;
	}

	/**
	 * FIXME: Well this is horrid // Hugi 2021-11-20
	 *
	 * What we're doing here is allowing for the WO URL structure, which is somewhat required to work with the WO Apache Adaptor.
	 * Ideally, we don't want to prefix URLs at all, instead just handling requests at root level. But to begin with, perhaps we can
	 * just allow for certain "prefix patterns" to mask out the WO part of the URL and hide it from the app. It might even be a useful
	 * little feature on it's own.
	 */
	private static void cleanupWOURL( final NGRequest request ) {
		String woStart = "/Apps/WebObjects/Rebelliant.woa/1";

		if( request.uri().startsWith( woStart ) ) {
			request.setURI( request.uri().substring( woStart.length() ) );
			logger.info( "Rewrote WO URI to {}", request.uri() );
		}

		woStart = "/cgi-bin/WebObjects/Rebelliant.woa";

		if( request.uri().startsWith( woStart ) ) {
			request.setURI( request.uri().substring( woStart.length() ) );
			logger.info( "Rewrote WO URI to {}", request.uri() );
		}
	}

	/**
	 * FIXME: Like the other "create..." methods, this one is inspired by WO. It's really a relic from the time when WOApplication served as The Central Thing Of All Things That Are.
	 * Good idea at the time, it made Wonder possible… But it's really just an older type of a factory or, well, dependency injection. Not sure we want to keep this way of constructing objects. // Hugi 2021-12-29
	 */
	public NGContext createContextForRequest( NGRequest request ) {
		return new NGContext( request );
	}

	/**
	 * Redirects logging to the designated [outputPath] if set.
	 *
	 * If a file exists at the given path, it is renamed by adding the current date to it's name.
	 * Pretty much the same way WOOutputPath is handled in WO.
	 */
	private static void initLogging( final String outputPath ) {
		if( outputPath != null ) {
			// Archive the older logFile if it exists
			final File outputFile = new File( outputPath );

			if( outputFile.exists() ) {
				final File oldOutputFile = new File( outputPath + "." + LocalDateTime.now() );
				outputFile.renameTo( oldOutputFile );
			}

			try {
				final PrintStream out = new PrintStream( new FileOutputStream( outputPath ) );
				System.setOut( out );
				System.setErr( out );
				logger.info( "Redirected System.out and System.err to {}", outputPath );
			}
			catch( final FileNotFoundException e ) {
				throw new RuntimeException( e );
			}
		}
		else {
			logger.info( "OutputPath not set. Using standard System.out and System.err" );
		}
	}

	/**
	 * FIXME: This is a bit harsh. We probably want to start some sort of a graceful shutdown procedure instead of saying "'K, BYE" // Hugi 2021-11-20
	 */
	public void terminate() {
		System.exit( 0 );
	}

	public NGElement dynamicElementWithName( final String name, final Map<String, NGAssociation> associations, final NGElement element, final List<String> languages ) {
		NGElement elementInstance = null;

		if( name == null ) {
			throw new IllegalArgumentException( "<" + "bla" + ">: No name provided for dynamic element creation." );
		}

		Class<? extends NGElement> elementClass = _NGUtilities.classWithName( name );

		if( elementClass != null && NGDynamicElement.class.isAssignableFrom( elementClass ) ) {
			Class[] params = new Class[] { String.class, Map.class, NGElement.class };
			Object[] arguments = new Object[] { name, associations, element };
			elementInstance = _NGUtilities.instantiateObject( elementClass, params, arguments );
		}

		if( elementInstance == null ) {
			NGComponentDefinition componentDefinition = this._componentDefinition( name, languages );
			if( componentDefinition != null ) {
				elementInstance = componentDefinition.componentReferenceWithAssociations( associations, element );
			}
		}

		return elementInstance;
	}

	/**
	 * @return The componentDefinition corresponding to the named WOComponent
	 *
	 * FIXME: Unsupported. Only here for template parsing experiment
	 */
	public NGComponentDefinition _componentDefinition( String componentName, List<String> languages ) {
		return _componentDefinition( _NGUtilities.classWithName( componentName ) );
	}
}