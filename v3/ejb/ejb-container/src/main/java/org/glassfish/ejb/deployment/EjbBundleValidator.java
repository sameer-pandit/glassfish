/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.ejb.deployment;

import java.util.*;
import java.util.logging.Level;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.ApplicationClientDescriptor;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.EjbDescriptor;
import com.sun.enterprise.deployment.DummyEjbDescriptor;
import com.sun.enterprise.deployment.EjbBundleDescriptor;
import com.sun.enterprise.deployment.EjbEntityDescriptor;
import com.sun.enterprise.deployment.EjbMessageBeanDescriptor;
import com.sun.enterprise.deployment.InjectionCapable;
import com.sun.enterprise.deployment.JmsDestinationReferenceDescriptor;
import com.sun.enterprise.deployment.MethodDescriptor;
import com.sun.enterprise.deployment.MessageDestinationDescriptor;
import com.sun.enterprise.deployment.MessageDestinationReferenceDescriptor;
import com.sun.enterprise.deployment.ResourceReferenceDescriptor;
import com.sun.enterprise.deployment.RunAsIdentityDescriptor;
import com.sun.enterprise.deployment.WebBundleDescriptor;
import com.sun.enterprise.deployment.WebService;
import com.sun.enterprise.deployment.InterceptorBindingDescriptor;
import com.sun.enterprise.deployment.util.ComponentValidator;
import com.sun.enterprise.deployment.util.EjbBundleVisitor;
import com.sun.enterprise.deployment.util.InterceptorBindingTranslator;
import com.sun.enterprise.deployment.util.DOLUtils;
import com.sun.enterprise.deployment.types.EjbReference;
import com.sun.enterprise.util.LocalStringManagerImpl;

/**
 * This class validates a EJB Bundle descriptor once loaded from an .jar file
 *
 * @author Jerome Dochez
 */
public class EjbBundleValidator  extends ComponentValidator implements EjbBundleVisitor {
    
    protected EjbBundleDescriptor ejbBundleDescriptor=null;
    protected EjbDescriptor ejb = null;
    private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(EjbBundleValidator.class);
            
    /** visits an ejb bundle descriptor
     * @param bundleDescriptor ejb bundle descriptor
     */
    public void accept(EjbBundleDescriptor bundleDescriptor) {
        if (bundleDescriptor.getEjbs().size() == 0) {
            throw new IllegalArgumentException(localStrings.getLocalString(
                "enterprise.deployment.util.no_ejb_in_ejb_jar",
                "Invalid ejb jar {0}: it contains zero ejb. A valid ejb jar requires at least one session/entity/message driven bean.", 
                new Object[] {bundleDescriptor.getModuleDescriptor().getArchiveUri()})); 
        }

	if (!bundleDescriptor.areResourceReferencesValid()) {
            throw new RuntimeException("Incorrectly resolved role references");
        }         

        this.ejbBundleDescriptor = bundleDescriptor;

        // Now that we have a classloader, we have to check for any
        // interceptor bindings that were specified in .xml to use
        // the syntax that refers to all overloaded methods with a
        // given name.  
        handleOverloadedInterceptorMethodBindings(bundleDescriptor);

        InterceptorBindingTranslator bindingTranslator = 
            new InterceptorBindingTranslator(bundleDescriptor);

        for(Iterator iter = bundleDescriptor.getEjbs().iterator(); 
            iter.hasNext();) {
            EjbDescriptor ejb = (EjbDescriptor) iter.next();
            if( ejb.getType() != EjbEntityDescriptor.TYPE ) {
                ejb.applyInterceptors(bindingTranslator);
            }
        }
    }

    private void handleOverloadedInterceptorMethodBindings(EjbBundleDescriptor
                                                           bundleDesc) {

        List<InterceptorBindingDescriptor> origBindings = 
            bundleDesc.getInterceptorBindings();

        if( origBindings.isEmpty() ) {
            return;
        }

        ClassLoader cl = bundleDesc.getClassLoader();

        List<InterceptorBindingDescriptor> newBindings = 
            new LinkedList<InterceptorBindingDescriptor>();

        for(InterceptorBindingDescriptor next : origBindings) {

            if( next.getNeedsOverloadResolution() ) {

                MethodDescriptor overloadedMethodDesc = 
                    next.getBusinessMethod();
                String methodName = overloadedMethodDesc.getName();
                // For method-specific interceptors, there must be an ejb-name.
                String ejbName = next.getEjbName();

                EjbDescriptor ejbDesc = bundleDesc.getEjbByName(ejbName);
                Class ejbClass = null;
           
                try {
                    ejbClass = cl.loadClass(ejbDesc.getEjbClassName());
                } catch(Exception e) {
                    RuntimeException re = new RuntimeException
                        ("Error loading ejb class "+ejbDesc.getEjbClassName());
                    re.initCause(e);
                    throw re;
                }

                for(Method ejbClassMethod : ejbClass.getDeclaredMethods()) {

                    if( ejbClassMethod.getName().equals(methodName) ) {

                        InterceptorBindingDescriptor newInterceptorBinding =
                            new InterceptorBindingDescriptor();

                        MethodDescriptor newMethodDesc = new MethodDescriptor
                            (ejbClassMethod, MethodDescriptor.EJB_BEAN);
                        
                        newInterceptorBinding.setEjbName(ejbName);
                        newInterceptorBinding.setBusinessMethod
                            (newMethodDesc);
                        for(String interceptorClass : 
                                next.getInterceptorClasses()) {
                            newInterceptorBinding.appendInterceptorClass
                                (interceptorClass);
                        }
                        newInterceptorBinding.setIsTotalOrdering
                            (next.getIsTotalOrdering());
                        newInterceptorBinding.setExcludeDefaultInterceptors
                            (next.getExcludeDefaultInterceptors());
                        newInterceptorBinding.setExcludeClassInterceptors
                            (next.getExcludeClassInterceptors());
                        
                        newBindings.add(newInterceptorBinding);

                    }

                }
                

            } else {

                newBindings.add(next);

            }

        }

        bundleDesc.setInterceptorBindings(newBindings);
    }

    /**
     * visits all entries within the component environment for which
     * isInjectable() == true.
     * @param injectable InjectionCapable environment dependency
     */
    public void accept(InjectionCapable injectable) {
        acceptWithCL(injectable);
        acceptWithoutCL(injectable);
    }

    /**
     * visits an ejb descriptor
     * @param ejb descriptor
     */
    public void accept(EjbDescriptor ejb) {
        // all the DummyEjbDescriptor which stored partial information from
        // xml should already be resolved to actual ejb descriptors.
        // if not, this means there is a referencing error in the user 
        // application
        if (ejb instanceof DummyEjbDescriptor) {
            throw new IllegalArgumentException(localStrings.getLocalString(
            "enterprise.deployment.exceptionbeanbundle",
            "Referencing error: this bundle has no bean of name: {0}",
            new Object[] {ejb.getName()}));
        }

        this.ejb =ejb;
        setDOLDefault(ejb);
        computeRuntimeDefault(ejb);

        try {

            ClassLoader cl = ejb.getEjbBundleDescriptor().getClassLoader();
            Class ejbClass = cl.loadClass(ejb.getEjbClassName());

            // Perform 2.x style TimedObject processing if the class 
            // hasn't already been identified as a timed object.  
            if( !ejb.isTimedObject() ) {

                if( javax.ejb.TimedObject.class.isAssignableFrom(ejbClass) ) {
                    MethodDescriptor timedObjectMethod = 
                        new MethodDescriptor("ejbTimeout", 
                                             "TimedObject timeout method",
                                             new String[] {"javax.ejb.Timer"},
                                             MethodDescriptor.EJB_BEAN);
                    ejb.setEjbTimeoutMethod(timedObjectMethod);
                }
            } else {
                // If timeout-method was only processed from the descriptor,
                // we need to create a MethodDescriptor using the actual
                // Method object corresponding to the timeout method.  The
                // timeout method can have any access type and be anywhere
                // in the bean class hierarchy.
                String timeoutMethodName = ejb.getEjbTimeoutMethod().getName();
                MethodDescriptor timeoutMethodDesc = null;
                Class nextClass = ejbClass;
                while((nextClass != Object.class) && (nextClass != null) 
                      && (timeoutMethodDesc == null) ) {
                    Method[] methods = nextClass.getDeclaredMethods();
                    for(Method m : methods) {
                        if( (m.getName().equals(timeoutMethodName)) ) {
                            Class[] params = m.getParameterTypes();
                            if( (params.length == 1) &&
                                (params[0] == javax.ejb.Timer.class) ) {
                                timeoutMethodDesc = new MethodDescriptor
                                    (m, MethodDescriptor.EJB_BEAN);
                                ejb.setEjbTimeoutMethod(timeoutMethodDesc);
                                break;
                            }
                        }
                    }
                    nextClass = nextClass.getSuperclass();
                }
            }

        } catch(Exception e) {
            RuntimeException re = new RuntimeException
                ("Error processing EjbDescriptor");
            re.initCause(e);
            throw re;
        }
        

    }    
        
    public void accept(WebService webService) {
    }

    /**
     * visits an ejb reference for the last J2EE component visited
     * @param ejbRef ejb reference
     */
    public void accept(EjbReference ejbRef) {
        DOLUtils.getDefaultLogger().fine("Visiting Ref" + ejbRef);
	if (ejbRef.getEjbDescriptor()!=null) 
            return;

        //
        // NOTE : In the 3.0 local/remote business view, the local vs.
        // remote designation is not always detectable from the interface 
        // itself.
        //
        // That means 
        // 
        // 1) we need to figure it out during this stage of the processing
        // 2) the EjbReferenceDescriptor.isLocal() operations shouldn't be
        //    be used before the post-application validation stage since its
        //    value would be unreliable.
        // 3) We can't write out the standard deployment descriptors to XML
        //    until the full application has been processed, including this
        //    validation stage.
        //
        // During @EJB processing, setLocal() is set to false if 
        // local vs. remote is ambiguous.  setLocal() is set to true within this 
        // method upon successfuly resolution to a local business interface.
        //

        if (ejbRef.getJndiName()!=null && ejbRef.getJndiName().length()!=0) {


            // ok this is getting a little complicated here
            // the jndi name is not null, if this is a remote ref, proceed with resolution
            // if this is a local ref, proceed with resolution only if ejb-link is null            
            if (!ejbRef.isLocal() || (ejbRef.isLocal() && ejbRef.getLinkName()==null)) {
                DOLUtils.getDefaultLogger().fine("Ref " + ejbRef.getName() + " is bound to Ejb with JNDI Name " + ejbRef.getJndiName());
                if (getEjbDescriptors() != null) {
                    for (Iterator iter = getEjbDescriptors().iterator(); iter.hasNext();) {
                        EjbDescriptor ejb = (EjbDescriptor)iter.next();

                        if (ejbRef.getJndiName().equals(ejb.getJndiName())) {
                            ejbRef.setEjbDescriptor(ejb);
                            return;
                        } 
                    }
                }
            }
        }

        // If the reference does not have an ejb-link or jndi-name associated
        // with it, attempt to resolve it by checking against all the ejbs
        // within the application.  If no match is found, just fall through
        // and let the existing error-checking logic kick in.
        if (( (ejbRef.getJndiName() == null) || 
              (ejbRef.getJndiName().length() == 0) )
            &&
            ( (ejbRef.getLinkName() == null) ||
              (ejbRef.getLinkName().length() == 0) )) {

            Map<String, EjbIntfInfo> ejbIntfInfoMap = getEjbIntfMap();
            if ( ejbIntfInfoMap.size() > 0 ) {

                String interfaceToMatch = ejbRef.isEJB30ClientView() ?
                    ejbRef.getEjbInterface() : ejbRef.getEjbHomeInterface();

                EjbIntfInfo intfInfo = ejbIntfInfoMap.get(interfaceToMatch);

                // make sure exactly one match
                if ( intfInfo != null ) {
                    int numMatches = intfInfo.ejbs.size();
                    if( numMatches == 1 ) {
                        Iterator iter = intfInfo.ejbs.iterator();

                        EjbDescriptor target = (EjbDescriptor)iter.next();

                        BundleDescriptor targetModule = 
                            target.getEjbBundleDescriptor();
                        BundleDescriptor sourceModule =
                            ejbRef.getReferringBundleDescriptor();

                        Application app = targetModule.getApplication();

                        //
                        // It's much cleaner to derive the ejb-link value
                        // and set that instead of the descriptor.  This way,
                        // if there are multiple ejb-jars within the .ear that
                        // each have an ejb with the target bean's ejb-name,
                        // there won't be any ambiguity about which one is
                        // the correct target.  It's not so much a problem
                        // during this phase of the processing, but if the
                        // fully-qualified ejb-link name is required and is not
                        // written out, there could be non-deterministic
                        // behavior when the application is re-loaded.
                        // Let the ejb-link processing logic handle the 
                        // conversion to ejb descriptor.
                        //

                        // If the ejb reference and the target ejb are defined
                        // within the same ejb-jar, the ejb-link will only
                        // be set to ejb-name.  This is done regardless of
                        // whether the ejb-jar is within an .ear or is
                        // stand-alone.  The ejb-link processing
                        // logic will always check the current ejb-jar
                        // first so there won't be any ambiguity.  
                        String ejbLinkName = target.getName();
                        if ( (sourceModule != targetModule) && (! app.isPackagedAsSingleModule()) ) {

                            // Since there are at least two modules, we
                            // must be within an application.
                            String relativeUri = getApplication().
                                getRelativeUri(sourceModule, targetModule);
                            ejbLinkName = relativeUri + "#" + ejbLinkName;
                        }

                        ejbRef.setLinkName(ejbLinkName);

                    } else {
                        String msg = "Cannot resolve reference " + ejbRef +
                            " because there are " + numMatches + " ejbs " +
                            " in the application with interface " + 
                            interfaceToMatch;

                        DOLUtils.getDefaultLogger().severe(msg);
                        throw new RuntimeException(msg);
                    }
                }                          
            } 
        }

        // now all cases fall back here, we need to resolve through the link-name    
        if (ejbRef.getLinkName()==null) {


            // if no link name if present, and this is a local ref, this is always an 
            // error because we must resolve all local refs and we cannot resolve it 
            // throw either the jndi name or the link name
            if (ejbRef.isLocal()) {
                DOLUtils.getDefaultLogger().severe("Cannot resolve reference " + ejbRef);
                throw new RuntimeException("Cannot resolve reference " + ejbRef);
            } else {
                // this is a remote interface, jndi will eventually contain the referenced 
                // ejb ref, apply default jndi name if there is none
                if (ejbRef.getJndiName() == null ||
                        ejbRef.getJndiName().length() == 0) {
                    String jndiName = getDefaultEjbJndiName(
                        ejbRef.isEJB30ClientView() ?
                        ejbRef.getEjbInterface() : ejbRef.getEjbHomeInterface());
                    ejbRef.setJndiName(jndiName);
                    DOLUtils.getDefaultLogger().fine("Applying default to ejb reference: " + ejbRef);
                }

                return;
            }                          
        }        
        
        // Beginning of ejb-link resolution
        
        // save anticipated types for checking if interfaces are compatible
        String homeClassName = ejbRef.getEjbHomeInterface();
        String intfClassName = ejbRef.getEjbInterface();

        // save anticipated type for checking if bean type is compatible
        String type = ejbRef.getType();
        
        EjbDescriptor ejbReferee=null;
            
        String linkName = ejbRef.getLinkName();
        int ind = linkName.lastIndexOf('#');
        if ( ind != -1 ) {
            // link has a relative path from referring EJB JAR,
            // of form "../products/product.jar#ProductEJB"
            String ejbName = linkName.substring(ind+1);
            String jarPath = linkName.substring(0, ind);            
            BundleDescriptor referringJar = ejbRef.getReferringBundleDescriptor();
            if (referringJar==null) {
                ejbRef.setReferringBundleDescriptor(getBundleDescriptor());
                referringJar = getBundleDescriptor();
            }           
            
            if (getApplication()!=null) {               
                BundleDescriptor refereeJar = 
                    getApplication().getRelativeBundle(referringJar, jarPath);
                if( (refereeJar != null) &&
                    refereeJar instanceof EjbBundleDescriptor ) {
                    // this will throw an exception if ejb is not found
                    ejbReferee = 
                       ((EjbBundleDescriptor)refereeJar).getEjbByName(ejbName);
                }
            }
        }
        else {

            // Handle an unqualified ejb-link, which is just an ejb-name.

            // If we're in an application and currently processing an
            // ejb-reference defined within an ejb-jar, first check
            // the current ejb-jar for an ejb-name match.  From a spec
            // perspective, the deployer can't depend on this behavior,
            // but it's still better to have deterministic results.  In
            // addition, in the case of automatic-linking, the fully-qualified
            // "#" ejb-link syntax is not used when the ejb reference and
            // target ejb are within the same ejb-jar.  Checking the
            // ejb-jar first will ensure the correct linking behavior for
            // that case.
            if ( (getApplication() != null) && (ejbBundleDescriptor != null)
                 && ejbBundleDescriptor.hasEjbByName(linkName) ) {

                ejbReferee = ejbBundleDescriptor.getEjbByName(linkName);

            } else if ( (getApplication() != null)  && 
                        getApplication().hasEjbByName(linkName)) {
                
                ejbReferee = 
                    getApplication().getEjbByName(ejbRef.getLinkName());
                    
            } else if (ejb!=null) {
                try {
                    ejbReferee = ejb.getEjbBundleDescriptor().getEjbByName(ejbRef.getLinkName());
                } catch (IllegalArgumentException e) {
                    // this may happen when we have no application and the ejb ref
                    // cannot be resolved to a ejb in the bundle. The ref will
                    // probably be resolved when the application is assembled.
                    DOLUtils.getDefaultLogger().warning("Unresolved <ejb-link>: "+linkName);
                    return;
                }
                    
            }
        } 

        if (ejbReferee==null)
        {  

            // we could not resolve through the ejb-link. if this is a local ref, this 
            // is an error, if this is a remote ref, this should be also an error at 
            // runtime but maybe the jndi name will be specified by deployer so 
            // a warning should suffice
            if (ejbRef.isLocal()) {
                DOLUtils.getDefaultLogger().severe("Unresolved <ejb-link>: "+linkName);
                throw new RuntimeException("Error: Unresolved <ejb-link>: "+linkName);
            } else {
                DOLUtils.getDefaultLogger().warning("Unresolved <ejb-link>: "+linkName);
                return;
            }
        } else {

            if( ejbRef.isEJB30ClientView() ) {

                BundleDescriptor referringBundle = 
                    ejbRef.getReferringBundleDescriptor();

                // If we can verify that the current ejb 3.0 reference is defined
                // in any Application Client module or in a stand-alone web module
                // it must be remote business.
                if( ( (referringBundle == null) && (ejbBundleDescriptor == null) )
                    ||
                    (referringBundle instanceof ApplicationClientDescriptor) 
                    ||
                    ( (getApplication() == null) &&
                      (referringBundle instanceof WebBundleDescriptor) ) ) {

                    ejbRef.setLocal(false);

                    // Double-check that target has a remote business interface of this
                    // type.  This will handle the common error case that the target 
                    // EJB has intended to support a remote business interface but
                    // has not used @Remote to specify it, in which case
                    // the interface was assigned the default of local business.

                    if( !ejbReferee.getRemoteBusinessClassNames().contains
                        (intfClassName) ) {
                        String msg = "Target ejb " + ejbReferee.getName() + " for " +
                            " remote ejb 3.0 reference " + ejbRef.getName() + 
                            " does not expose a remote business interface of type " +
                            intfClassName;
                        throw new RuntimeException(msg);
                    }

                } else if(ejbReferee.getLocalBusinessClassNames().
                         contains(intfClassName)) {

                    ejbRef.setLocal(true);

                } else if(ejbReferee.getRemoteBusinessClassNames().
                          contains(intfClassName)) {

                    ejbRef.setLocal(false);

                } else {
                    if (ejbReferee.isOptionalLocalBusinessViewSupported()) {
                        ejbRef.setLocal(true);    
                    } else {
                        String msg = "Warning : Unable to determine local " +
                            " business vs. remote business designation for " +
                            " EJB 3.0 ref " + ejbRef;
                        throw new RuntimeException(msg);
                    }
                }
            }

            ejbRef.setEjbDescriptor(ejbReferee);
        }
        
        // if we are here, we must have resolved the reference
        
	if(DOLUtils.getDefaultLogger().isLoggable(Level.FINE)) {
            DOLUtils.getDefaultLogger().fine("Done Visiting " + ejb.getName() + " reference " + ejbRef);
	}

        // check that declared types are compatible with expected values
        // if there is a target ejb descriptor available
        if( ejbReferee != null ) {

            if( ejbRef.isEJB30ClientView() ) {

                Set<String> targetBusinessIntfs = ejbRef.isLocal() ?
                    ejbReferee.getLocalBusinessClassNames() :
                    ejbReferee.getRemoteBusinessClassNames();

                if( !targetBusinessIntfs.contains(intfClassName) ) {

                    DOLUtils.getDefaultLogger().log(Level.WARNING, 
                       "enterprise.deployment.backend.ejbRefTypeMismatch",
                       new Object[] {ejbRef.getName() , intfClassName, 
                       ejbReferee.getName(), ( ejbRef.isLocal() ? 
                       "Local Business" : "Remote Business"), 
                                         targetBusinessIntfs.toString()});

                    // We can only figure out what the correct type should be
                    // if there is only 1 target remote/local business intf.
                    if( targetBusinessIntfs.size() == 1 ) {
                        Iterator iter = targetBusinessIntfs.iterator();
                        ejbRef.setEjbInterface((String)iter.next());
                    }
                }

            } else {

                String targetHome = ejbRef.isLocal() ? 
                    ejbReferee.getLocalHomeClassName() : 
                    ejbReferee.getHomeClassName();

                if( !homeClassName.equals(targetHome) ) {

                    DOLUtils.getDefaultLogger().log(Level.WARNING, 
                       "enterprise.deployment.backend.ejbRefTypeMismatch",
                       new Object[] {ejbRef.getName() , homeClassName, 
                       ejbReferee.getName(), ( ejbRef.isLocal() ? 
                       "Local Home" : "Remote Home"), targetHome});

                    if( targetHome != null ) {
                        ejbRef.setEjbHomeInterface(targetHome);
                    }
                }

                String targetComponentIntf = ejbRef.isLocal() ?
                    ejbReferee.getLocalClassName() : 
                    ejbReferee.getRemoteClassName();

                // In some cases for 2.x style @EJBs that point to Entity beans
                // the interface class cannot be derived, so only do the
                // check if the intf is known.
                if( (intfClassName != null) &&
                    !intfClassName.equals(targetComponentIntf) ) {

                    DOLUtils.getDefaultLogger().log(Level.WARNING, 
                       "enterprise.deployment.backend.ejbRefTypeMismatch",
                       new Object[] {ejbRef.getName() , intfClassName, 
                       ejbReferee.getName(), ( ejbRef.isLocal() ? 
                       "Local" : "Remote"), targetComponentIntf});

                    if( targetComponentIntf != null ) {
                        ejbRef.setEjbInterface(targetComponentIntf);
                    }
                }
            }
        }

        if (type == null) { 
            // ejb-ref type is now optional
            // in that case, set the type
            // note: the ejbRef.getType gets the type from
            // its referencing ejb bundle descriptor
            ejbRef.setType(ejbRef.getType());
        } else if (!type.equals(ejbRef.getType())) {
            // or if they don't match 
            // print a warning and reset the type in ejb ref
            DOLUtils.getDefaultLogger().log(Level.WARNING, "enterprise.deployment.backend.invalidDescriptorMappingFailure",
            new Object[] {ejbRef.getName() , type});

            ejbRef.setType(ejbRef.getType());

        }          
    }        

    public void accept(ResourceReferenceDescriptor resRef) {
        computeRuntimeDefault(resRef);
    }

    public void accept(JmsDestinationReferenceDescriptor jmsDestRef) {
        computeRuntimeDefault(jmsDestRef);
    }

    public void accept(MessageDestinationReferenceDescriptor msgDestRef) {
        computeRuntimeDefault(msgDestRef);
    }

    public void accept(MessageDestinationDescriptor msgDest) {
        computeRuntimeDefault(msgDest);
    }

    /**
     * Returns a map of interface name -> EjbIntfInfo based on all the ejbs
     * within the application or stand-alone module.  Only RemoteHome, 
     * RemoteBusiness, LocalHome, and LocalBusiness are eligible for map. 
     */
    private Map<String, EjbIntfInfo> getEjbIntfMap() {

        Collection ejbs = getEjbDescriptors();

        Map<String, EjbIntfInfo> intfInfoMap=new HashMap<String, EjbIntfInfo>();

        for(Iterator iter = ejbs.iterator(); iter.hasNext();) {
            EjbDescriptor next = (EjbDescriptor) iter.next();

            if( next.isRemoteInterfacesSupported() ) {
                addIntfInfo(intfInfoMap, next.getHomeClassName(), 
                            EjbIntfType.REMOTE_HOME, next);
            }

            if( next.isRemoteBusinessInterfacesSupported() ) {
                for(String nextIntf : next.getRemoteBusinessClassNames()) {
                    addIntfInfo(intfInfoMap, nextIntf, 
                                EjbIntfType.REMOTE_BUSINESS, next);
                }
            }

            if( next.isLocalInterfacesSupported() ) {
                addIntfInfo(intfInfoMap, next.getLocalHomeClassName(), 
                            EjbIntfType.LOCAL_HOME, next);
            }

            if( next.isLocalBusinessInterfacesSupported() ) {
                for(String nextIntf : next.getLocalBusinessClassNames()) {
                    addIntfInfo(intfInfoMap, nextIntf, 
                                EjbIntfType.LOCAL_BUSINESS, next);
                }
            }

            if (next.isOptionalLocalBusinessViewSupported()) {
                addIntfInfo(intfInfoMap, next.getEjbClassName(),
                                EjbIntfType.NO_INTF_LOCAL_BUSINESS, next);
            }

        }

        return intfInfoMap;
    }
    
    private void addIntfInfo(Map<String, EjbIntfInfo> intfInfoMap,
                             String intf, EjbIntfType intfType,
                             EjbDescriptor ejbDesc) {

        EjbIntfInfo intfInfo = intfInfoMap.get(intf);
        if( intfInfo == null ) {
            EjbIntfInfo newInfo = new EjbIntfInfo();
            newInfo.ejbs = new HashSet<EjbDescriptor>();
            newInfo.ejbs.add(ejbDesc);
            newInfo.intfType = intfType;
            intfInfoMap.put(intf, newInfo);
        } else {
            intfInfo.ejbs.add(ejbDesc);
            // Since there's more than one match, reset intf type.
            intfInfo.intfType = EjbIntfType.NONE;
        }

    }

    /**
     * @return a vector of EjbDescriptor for this bundle
     */
    protected Collection getEjbDescriptors() {
        if (getApplication() != null) {
            return getApplication().getEjbDescriptors();
        } else if (ejbBundleDescriptor!=null) {
            return ejbBundleDescriptor.getEjbs();
        } else {
            return new HashSet();
        }
    } 
    
    /**
     * @return the Application object if any
     */
    protected Application getApplication() {
        return null;
    }
     
    /**
     * @return the bundleDescriptor we are validating
     */
    protected BundleDescriptor getBundleDescriptor() {
        return ejbBundleDescriptor;
    }

    /**
     * Set a default RunAs principal to given RunAsIdentityDescriptor
     * if necessary.
     * @param runAs
     * @param application
     * @exception RuntimeException
     */
    protected void computeRunAsPrincipalDefault(RunAsIdentityDescriptor runAs,
            Application application) {

        // for backward compatibile
        if (runAs != null &&
                (runAs.getRoleName() == null ||
                    runAs.getRoleName().length() == 0)) {
            DOLUtils.getDefaultLogger().log(Level.WARNING,
            "enterprise.deployment.backend.emptyRoleName");
            return;
        }
        
        if (runAs != null &&
                (runAs.getPrincipal() == null ||
                    runAs.getPrincipal().length() == 0) &&
                application != null && application.getRoleMapper() != null) {

            String principalName = null;
            String roleName = runAs.getRoleName();

            final Subject fs = (Subject)application.getRoleMapper().getRoleToSubjectMapping().get(roleName);
            if (fs != null) {
                principalName = (String)AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        Set<Principal> pset = fs.getPrincipals();
                        Principal prin = null;
                        if (pset.size() > 0) {
                            prin = (Principal)pset.iterator().next();
                            DOLUtils.getDefaultLogger().log(Level.WARNING, 
                            "enterprise.deployment.backend.computeRunAsPrincipal",
                            new Object[] { prin.getName() });
                        }
                        return (prin != null) ? prin.getName() : null;
                    }
                });
            }

            if (principalName == null || principalName.length() == 0) {
                throw new RuntimeException("The RunAs role " + "\"" + roleName + "\"" +
                    " is not mapped to a principal.");
            }
            runAs.setPrincipal(principalName);
        }
    }

    /**
     * Set default value for EjbDescriptor.
     */
    private void setDOLDefault(EjbDescriptor ejb) {
        if (ejb.getUsesCallerIdentity() == null) {
            if (ejb instanceof EjbMessageBeanDescriptor) {
                ejb.setUsesCallerIdentity(false);
            } else {
                ejb.setUsesCallerIdentity(true);
            }
        }
        // for ejb 3.0
        if (ejb.getTransactionType() == null) {
            ejb.setTransactionType(EjbDescriptor.CONTAINER_TRANSACTION_TYPE);
        }
        ejb.setUsesDefaultTransaction();
    }

    /**
     * Set runtime default value for EjbDescriptor.
     */
    private void computeRuntimeDefault(EjbDescriptor ejb) {
        String intfName = null;

        if ((ejb.getJndiName() == null) || (ejb.getJndiName().length() == 0)) {
            if (ejb.isRemoteInterfacesSupported() && ejb.isRemoteBusinessInterfacesSupported()) {
                 // can't use a default.
            } else if (ejb.isRemoteInterfacesSupported()) {
                 // For 2.x view, use the Home as the basis for the default
                 intfName = ejb.getHomeClassName();
            } else if (ejb.isRemoteBusinessInterfacesSupported()) {
                 Set<String> classNames = ejb.getRemoteBusinessClassNames();
                 if (classNames.size() == 1) {
                     intfName = (String)classNames.iterator().next();
                 }
            }
        }

        if( intfName != null ) {
            String jndiName = getDefaultEjbJndiName(intfName);
            ejb.setJndiName(jndiName);
        } 

        if (!ejb.getUsesCallerIdentity()) {
            computeRunAsPrincipalDefault(
                ejb.getRunAsIdentity(), ejb.getApplication());
        }
    }

    /**
     * Set runtime default value for ResourceReferenceDescriptor.
     */
    private void computeRuntimeDefault(ResourceReferenceDescriptor resRef) {
        if (resRef.getType().equals("org.omg.CORBA.ORB")) {
            resRef.setJndiName("java:comp/ORB");
        }

        else if (resRef.getJndiName() == null ||
                resRef.getJndiName().length() == 0) {
            resRef.setJndiName(getDefaultResourceJndiName(resRef.getName()));
        } 
    }

    /**
     * Set runtime default value for JmsDestinationReferenceDescriptor. 
     */
    private void computeRuntimeDefault(JmsDestinationReferenceDescriptor jmsDestRef) {
        if (jmsDestRef.getRefType().equals(
            "javax.transaction.UserTransaction")) {
            jmsDestRef.setJndiName("java:comp/UserTransaction");
        }

        else if (jmsDestRef.getRefType().equals(
            "javax.transaction.TransactionSynchronizationRegistry")) {
            jmsDestRef.setJndiName(
                "java:comp/TransactionSynchronizationRegistry");
        }

        else if (jmsDestRef.getJndiName() == null ||
                jmsDestRef.getJndiName().length() == 0) {
            jmsDestRef.setJndiName(getDefaultResourceJndiName(jmsDestRef.getName()));
        }
    }
    
    /**
     * Set runtime default value for MessageDestinationReferenceDescriptor.
     */
    private void computeRuntimeDefault(MessageDestinationReferenceDescriptor msgDestRef) {
        if (msgDestRef.getJndiName() == null ||
                msgDestRef.getJndiName().length() == 0) {
            msgDestRef.setJndiName(getDefaultResourceJndiName(msgDestRef.getName()));
        }
    }

    /**
     * Set runtime default value for MessageDestinationDescriptor. 
     */
    private void computeRuntimeDefault(MessageDestinationDescriptor msgDest) {
        if (msgDest.getJndiName() == null ||
                msgDest.getJndiName().length() == 0) {
            msgDest.setJndiName(getDefaultResourceJndiName(msgDest.getName()));
        }
    }

    /**
     * @param resName
     * @return default jndi name for a given interface resource name
     */
    private String getDefaultResourceJndiName(String resName) {
        return resName;
    }

    /**
     * @param intfName
     * @return default jndi name for a given interface name
     */
    //XXX this is first implementation. It does not handle two ejb with same
    //    interface in different jar
    private String getDefaultEjbJndiName(String intfName) {
        return intfName;
    }

    private enum EjbIntfType {
        NONE,
        REMOTE_HOME,
        REMOTE_BUSINESS,
        LOCAL_HOME,
        LOCAL_BUSINESS,
        NO_INTF_LOCAL_BUSINESS
    }

    private static class EjbIntfInfo {

        Set<EjbDescriptor> ejbs;

        // Only set when there is one ejb in the set. 
        // Otherwise, value = NONE
        EjbIntfType intfType;
    }
    
}
