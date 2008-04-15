/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
/*
 * LocalServiceRegistry.java
 *
 * Created on November 7, 2007, 1:15 PM
 *
 */
package com.sun.enterprise.registration;

/**
*
* This class generates a local registry file thats going to be stored under 
* install/lib dir. The service tag attributes are stored in this file for a 
* particular installation.
*
*/
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Properties;
import java.net.ConnectException;
import java.net.UnknownHostException;
import com.sun.scn.servicetags.SvcTag;
//import com.sun.enterprise.util.RegistrationUtil;
//import com.sun.enterprise.registration.SysnetRegistrationService;

public class LocalServiceRegistry {
    
    private ServiceTag  servicetag;

    //Revist: make the debug to true for debugging purpose
    boolean debug = false;
	
    private final static  LocalServiceRegistry registry = new LocalServiceRegistry();
    /** Creates a new instance of ServiceRegistry */
    private LocalServiceRegistry() {
	createSerivceRegistryFile();
    }

    public static LocalServiceRegistry getLocalRegistry() {
	 return registry;
    }
    /**
     * @param args the command line arguments
    */
    public static void main(String[] args) {
	
        Properties p = System.getProperties();
	//p.list(System.out);
	System.out.println("From Local service registry  ..");
	LocalServiceRegistry reg = LocalServiceRegistry.getLocalRegistry();
	//Revisit: set the debug flag from ant file itself
	//reg.showServiceTagAttributes();
    }

    private void createSerivceRegistryFile() {
        Properties data = System.getProperties();

        //String registryName = SysnetRegistrationService.getRepositoryFile();
	//RegistrationUtil.getServiceTagRegistry();

	//create product_defined_inst_id tag

	String registryName = System.getProperty("srvcRegisFileName");
	try {
	    File registry =  new File(registryName);
            RepositoryManager rm = new RepositoryManager(registry);
            ServiceTag st = new ServiceTag(data);
	    rm.add(st);
        } catch(RegistrationException ex) {
            //ex.printStackTrace();
        }
    }

    public  void showServiceTagAttributes() {
        Properties data = System.getProperties();
	showAttribute(ServiceTag.PRODUCT_NAME);
	showAttribute(ServiceTag.PRODUCT_VERSION);
	showAttribute(ServiceTag.PRODUCT_URN);
	showAttribute(ServiceTag.PRODUCT_PARENT);
	showAttribute(ServiceTag.PRODUCT_PARENT_URN);
	showAttribute(ServiceTag.PRODUCT_DEFINED_INST_ID);
	showAttribute(ServiceTag.CONTAINER);
	showAttribute(ServiceTag.SOURCE);
	showAttribute(ServiceTag.INSTANCE_URN);
	showAttribute(ServiceTag.STATUS);
	showAttribute(ServiceTag.REGISTRATION_STATUS);
	showAttribute(ServiceTag.SERVICE_TAG);
        //List<ServiceTag> list = rm.getServiceTags();
        //System.out.println("List of service tags:");
        //for (ServiceTag tag : list) {
              //System.out.println(tag.toString());
        //}
        //rm.write(new FileOutputStream("test.xml"));
    }
    
    public  void showAttribute(String key) {
	if (debug) {
	    System.out.println(key +" = " + System.getProperty(key));
	}
    }    
}
