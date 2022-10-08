package ng.appserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ng.appserver.directactions.NGDirectActionRequestHandler;
import ng.appserver.routing.NGRouteTable;
import ng.appserver.templating._NGUtilities;
import ng.appserver.wointegration.NGDefaultLifeBeatThread;
import ng.appserver.wointegration.WOMPRequestHandler;
import x.junk.NGExceptionPage;

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

		final NGProperties properties = new NGProperties( args );

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

			_application.start();
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

	/**
	 * Starts the adaptor
	 */
	private void start() {
		try {
			createAdaptor().start();
		}
		catch( final Exception e ) {
			throw new RuntimeException( e );
		}
	}

	/**
	 * @return The class to use when constructing a new session. By default we look for a class named "Session" in the same package as the application class
	 */
	protected Class<? extends NGSession> _sessionClass() {
		try {
			return (Class<? extends NGSession>)Class.forName( getClass().getPackageName() + ".Session" );
		}
		catch( ClassNotFoundException e ) {
			logger.info( "Custom session class not found. Defaulting to " + NGSession.class.getName() );
			return NGSession.class;
		}
	}

	/**
	 * FIXME: This should eventually return the name of our own adaptor. Using Jetty for now (since it's easier to implement) // Hugi 2021-12-29
	 */
	public String adaptorClassName() {
		return "ng.adaptor.jetty.NGAdaptorJetty";
		//		return ng.adaptor.raw.NGAdaptorRaw.class.getName();
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
	 * Return the named component, where [componentName] can be ither the component's simple class name or full class name.
	 */
	public NGComponent pageWithName( final String componentName, final NGContext context ) {
		return pageWithName( _NGUtilities.classWithName( componentName ), context );
	}

	/**
	 * @return A new instance of [componentClass] in the given [context]
	 */
	public <E extends NGComponent> E pageWithName( final Class<E> componentClass, final NGContext context ) {
		Objects.requireNonNull( componentClass, "'componentClass' must not be null. I can't create components from nothing." );
		Objects.requireNonNull( componentClass, "'context' must not be null. What's life without context?" );

		final NGComponentDefinition definition = _componentDefinition( componentClass, Collections.emptyList() );

		if( definition == null ) {
			throw new RuntimeException( "No such component definition: " + componentClass );
		}

		E componentInstance = (E)definition.componentInstanceInContext( context );

		// FIXME: I'm not sure we should be setting the context's page here. But it works for us for now // Hugi 2022-06-25
		context.setPage( componentInstance );

		return componentInstance;
	}

	@Deprecated
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

		try {
			cleanupWOURL( request );

			// FIXME: Handle the case of no default request handler gracefully // Hugi 2021-12-29
			if( request.parsedURI().length() == 0 ) {
				return defaultResponse( request ).generateResponse();
			}

			final NGRequestHandler requestHandler = _routeTable.handlerForURL( request.uri() );

			if( requestHandler == null ) {
				return new NGResponse( "No request handler found for uri " + request.uri(), 404 );
			}

			// FIXME: We might want to look into a little more exception handling // Hugi 2022-04-18
			final NGResponse response = requestHandler.handleRequest( request );

			if( response == null ) {
				throw new NullPointerException( String.format( "'%s' returned a null response. That's just rude.", requestHandler.getClass().getName() ) );
			}

			return response;
		}
		catch( Throwable throwable ) {
			handleException( throwable );
			return exceptionResponse( throwable, request.context() ).generateResponse();
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
	 * FIXME: Allow for different exception responses for production/development environments // Hugi 2022-04-20
	 */
	public NGActionResults exceptionResponse( final Throwable throwable, final NGContext context ) {
		final NGExceptionPage nextPage = pageWithName( NGExceptionPage.class, context );
		nextPage.setException( throwable );
		return nextPage;
	}

	/**
	 * @return A  response generated when an exception occurs
	 *
	 * FIXME: I'm letting this be for now, while we mull over if we wants thing to work without components/templating // Hugi 2022-10-08
	 */
	@Deprecated
	private NGActionResults rawExceptionResponse( final Throwable throwable, final NGContext context ) {
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
	public NGActionResults defaultResponse( final NGRequest request ) {
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
	private void cleanupWOURL( final NGRequest request ) {

		String woStart = "/Apps/WebObjects/%s.woa/1".formatted( properties().propWOApplicationName() );

		if( request.uri().startsWith( woStart ) ) {
			request.setURI( request.uri().substring( woStart.length() ) );
			logger.info( "Rewrote WO URI to {}", request.uri() );
		}

		woStart = "/cgi-bin/WebObjects/%s.woa".formatted( properties().propWOApplicationName() );

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

	/**
	 * @return The componentDefinition corresponding to the given WOComponent class.
	 *
	 * FIXME: This is currently extremely simplistic. We need to check for the existence of a definition, add localization etc. // Hugi 2022-01-16
	 * FIXME: This should not be static, belongs in an instance of a different class.
	 */
	private static NGComponentDefinition _componentDefinition( final Class<? extends NGComponent> componentClass, final List<String> languages ) {
		Objects.requireNonNull( componentClass );
		Objects.requireNonNull( languages );

		return new NGComponentDefinition( componentClass );
	}

	/**
	 * @return The componentDefinition corresponding to the named WOComponent
	 *
	 * FIXME: Kind of unsupported. We really only want to allow components that have a class, and in these cases we should have loaded the component's class earlier in the process.
	 * FIXME: Languages aren't supported either yet, but I'm including the parameter while I consider what to do about it.
	 * FIXME: This should not be static, belongs in an instance of a different class.
	 */
	public static NGComponentDefinition _componentDefinition( final String componentName, final List<String> languages ) {
		Objects.requireNonNull( componentName );
		Objects.requireNonNull( languages );

		final Class<? extends NGComponent> componentClass = _NGUtilities.classWithName( componentName );
		return _componentDefinition( componentClass, languages );
	}

	/**
	 * FIXME: This should not be static, belongs in an instance of a different class.
	 * FIXME: If we're going to introduce namespaces, this would be the place
	 */
	public static NGElement dynamicElementWithName( final String name, final Map<String, NGAssociation> associations, final NGElement contentTemplate, final List<String> languages ) {
		Objects.requireNonNull( name );
		Objects.requireNonNull( associations );
		Objects.requireNonNull( languages );

		// First we locate the class of the element we're going to render.
		final Class<? extends NGElement> elementClass = _NGUtilities.classWithName( name );

		// FIXME: Starting out with this hard stop will not be compatible with classless components // Hugi 2022-10-08
		if( elementClass == null ) {
			throw new IllegalArgumentException( "Element class '%s' not found".formatted( name ) );
		}

		// First we check if this is a dynamic element
		if( NGDynamicElement.class.isAssignableFrom( elementClass ) ) {
			final Class<?>[] params = { String.class, Map.class, NGElement.class };
			final Object[] arguments = { name, associations, contentTemplate };
			return _NGUtilities.instantiateObject( elementClass, params, arguments );
		}

		// If it's not an element, let's move on to creating a component reference instead
		if( NGComponent.class.isAssignableFrom( elementClass ) ) {
			final NGComponentDefinition componentDefinition = _componentDefinition( (Class<? extends NGComponent>)elementClass, languages );

			if( componentDefinition == null ) {
				throw new IllegalArgumentException( "Failed to construct a component definition for '%s'".formatted( name ) );
			}

			return componentDefinition.componentReferenceWithAssociations( associations, contentTemplate );
		}

		// We should never end up here unless we got an incorrect/non-existent element name
		throw new IllegalArgumentException( "I could not construct a dynamic element named '%s'".formatted( name ) );
	}
}