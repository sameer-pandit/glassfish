/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.enterprise.v3.admin;

import java.util.*;
import org.glassfish.external.probe.provider.annotations.Probe;
import org.glassfish.external.probe.provider.annotations.ProbeParam;
import org.glassfish.external.probe.provider.annotations.ProbeProvider;

/**
 *
 * @author bnevins
 */
@ProbeProvider (moduleProviderName="glassfish", moduleName="kernel", probeProviderName="PPTester")
public class PPTester {
    @Probe(name="method1")
    public void method1(String s, int i) {
        System.out.println("HELLO FROM PPTester.method1  ARgs:" + s + ", " + i);
    }

    @Probe(name="method2")
     public void method2(String s23, int x, int y, Date date) {
        System.out.println("HELLO FROM PPTester.method2!  Date=" + date);
     }

    @Probe(name="method3")
     public void method3(String s){
        System.out.println("HELLO FROM PPTester.method3!  My Arg ==>" + s);
     }
}
