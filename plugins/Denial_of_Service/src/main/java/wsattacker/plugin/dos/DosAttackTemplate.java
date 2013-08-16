/**
 * WS-Attacker - A Modular Web Services Penetration Testing Framework Copyright
 * (C) 2012 Andreas Falkenberg
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
package wsattacker.plugin.dos;

import com.eviware.soapui.impl.wsdl.WsdlRequest;
import wsattacker.plugin.dos.dosExtension.abstractPlugin.AbstractDosPlugin;

import java.util.HashMap;
import java.util.Map;
import wsattacker.plugin.dos.dosExtension.option.OptionTextAreaSoapMessage;

/**
 * This is a DoS attack template.
 * When creating a new attack implementation just copy
 * this class, rename it and adjust the implementations of the methods.
 * The method createTamperedRequest() provides two examples of how to access the
 * original SOAP request
 * When done with the implementation go to the file
 * "wsattacker.main.composition.plugin.AbstractPlugin"
 * and add your new attack to the list.
 * The file can be found in the folder: "src/main/resources/META-INF/services"
 *
 * @author Andreas Falkenberg
 */
public class DosAttackTemplate extends AbstractDosPlugin {

    // Mandatory DOS-specific Attributes - Do NOT change!
    // <editor-fold defaultstate="collapsed" desc="Autogenerated Attributes">
    private static final long serialVersionUID = 1L;

    @Override
    public void initializeDosPlugin() {
        initData();
    }

    @Override
    public OptionTextAreaSoapMessage.PayloadPosition getPayloadPosition() {
        // in this template no payload placeholder will get set
        // the original test request will remain untouched
        return OptionTextAreaSoapMessage.PayloadPosition.NONE;
    }

    public void initData() {
        setName("Test DOS Attack");
        setDescription("This attack demonstrates the correct functioning of the DOS extension.\n"
          + "The attack request is identical to the original untampered request."
          + "No Payload is injected in request!"
          + "\n\n");
        setCountermeasures("No Countermeasure! This is just a test");
    }

    @Override
    public void createTamperedRequest() {

        // create tampered message by using method 1 or 2
        // - METHOD 1 - get SOAP message from user as set in attack parameter "Message"
        //   if getPayloadPosition() was set to other than NONE
        //   make sure to replace the payload placeholder with the actual payload
        //   by calling replacePlaceholderWithPayload() as shown.
        String msg1 = getOptionTextAreaSoapMessage().getValue();
        // createPayloadString();
        msg1 = this.getOptionTextAreaSoapMessage().replacePlaceholderWithPayload(msg1, new String(""));


        // - METHOD 2 - get message from original WsdlRequest by making copy
        WsdlRequest r = getOriginalRequest().getOperation().addNewRequest(getName() + " tampered");
        String msg2 = r.getRequestContent();
        // manipulate request string msg2...

        // get HeaderFields from original request, if required add custom headers - make sure to clone!
        Map<String, String> httpHeaderMap = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : getOriginalRequestHeaderFields().entrySet()) {
            httpHeaderMap.put(entry.getKey(), entry.getValue());
        }

        // write payload and header to TamperedRequestObject
        this.setTamperedRequestObject(httpHeaderMap, getOriginalRequest().getEndpoint(), msg1);

    }
    // ----------------------------------------------------------
    // All custom DoS attack specific methods below!
    // ----------------------------------------------------------
    // Nothing in this template
    // ...
}