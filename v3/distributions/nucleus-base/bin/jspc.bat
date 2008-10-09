@echo off
REM
REM Copyright 1997-2008 Sun Microsystems, Inc.  All rights reserved.
REM Use is subject to license terms.
REM

set AS_INSTALL_LIB=%~dp0..\modules
set ANT_LIB=%AS_INSTALL_LIB%\ant.jar
set SERVLET_API=%AS_INSTALL_LIB%\javax.servlet.jar
set JSP_API=%AS_INSTALL_LIB%\javax.servlet.jsp.jar
set JSP_IMPL=%AS_INSTALL_LIB%\web\jsp-impl.jar
set EL_IMPL=%AS_INSTALL_LIB%\web\el-impl.jar
set JSTL_API=%AS_INSTALL_LIB%\web\javax.servlet.jsp.jstl.jar
set JSTL_IMPL=%AS_INSTALL_LIB%\web\jstl-impl.jar
set JSF_API=%AS_INSTALL_LIB%\jsf-api.jar
set JSF_IMPL=%AS_INSTALL_LIB%\web\jsf-impl.jar

java -cp "%SERVLET_API%;%JSP_API%;%JSTL_API%;%JSF_API%;%ANT_LIB%;%EL_IMPL%;%JSP_IMPL%" org.apache.jasper.JspC -classpath "%JSTL_IMPL%;%JSF_IMPL%" %*
