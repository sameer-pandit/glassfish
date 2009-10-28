/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.enterprise.v3.admin;

import java.util.*;
import org.glassfish.external.probe.provider.annotations.ProbeListener;
import org.glassfish.external.probe.provider.annotations.ProbeParam;

/**
 *
 * @author bnevins
 */
public class PPListener {
    @ProbeListener("glassfish:kernel:PPTester:method1")
    public void listen1(String s, int i) {
        System.out.println("@@@@@@@@@@@@@@@@@@@  PPTester Listener 1   @@@@@@@@@@@@@@@@@@@@@@@");
    }

    @ProbeListener("glassfish:kernel:PPTester:method2")
    public void listen2(String s23, int x, int y, Date date) {
        System.out.println("@@@@@@@@@@@@@@@@@@@  PPTester Listener 2  Date=" + date + " @@@@@@@@@@@@@@@@@@@@@@@");
    }

    @ProbeListener("glassfish:kernel:PPTester:method3")
    public void listen3(String s) {
        System.out.println("@@@@@@@@@@@@@@@@@@@  PPTester Listener 3   @@@@@@@@@@@@@@@@@@@@@@@");
    }
}