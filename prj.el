;;This file assumes that the following variables and methods exist:
;; catalina-home - points to tomcat install directory

(jde-set-project-name "fll-sw")
(let ((project-root (file-name-directory load-file-name))
      )
  ;; Setup TAGS list
  (let ((tag-cons (cons
                   (expand-file-name "src" project-root)
                   (expand-file-name "src" project-root))))
    (if (boundp 'tag-table-alist)
        (add-to-list 'tag-table-alist tag-cons)
      (setq tag-table-alist (list tag-cons))))
  ;; JDE customizations
  (jde-set-variables
   '(jde-run-working-directory (expand-file-name "build/" project-root))
   '(jde-compile-option-directory (expand-file-name "build/" project-root))
   '(jde-run-read-app-args t)
   '(jde-global-classpath
     (list
      (expand-file-name "build/web/WEB-INF/classes" project-root)
			   
      (expand-file-name "lib/JonsInfra-0.2.jar" project-root)
      (expand-file-name "lib/junit-3.8.1.jar" project-root)
      (expand-file-name "lib/log4j-1.2.8.jar" project-root)
      (expand-file-name "lib/mysql-connector-java-3.0.8-stable-bin.jar" project-root)
      (expand-file-name "lib/common-lib/xercesImpl.jar" project-root)
      (expand-file-name "lib/common-lib/xmlParserAPIs.jar" project-root)

      ;; JSTL libraries
      (expand-file-name "lib/dom.jar" project-root)
      (expand-file-name "lib/jaxen-full.jar" project-root)
      (expand-file-name "lib/jaxp-api.jar" project-root)
      (expand-file-name "lib/jstl.jar" project-root)
      (expand-file-name "lib/sax.jar" project-root)
      (expand-file-name "lib/saxpath.jar" project-root)
      (expand-file-name "lib/standard.jar" project-root)
      (expand-file-name "lib/xalan.jar" project-root)

      ;; standard stuff
      (expand-file-name "common/lib/servlet.jar" catalina-home)
      ))
   
   '(jde-compile-option-deprecation t)
   '(jde-build-function 		'(jde-ant-build))
   '(jde-ant-working-directory		project-root)
   '(jde-ant-read-target 		t) ;; prompt for the target name
   '(jde-ant-enable-find 		t) ;; make jde-ant look for the build file
   '(jde-ant-complete-target 		nil) ;; don't parse the build file for me
   '(jde-ant-invocation-method  	'("Java"))
   '(jde-run-option-vm-args '("-DASSERT_BEHAVIOR=CONTINUE "))
 '(jde-gen-buffer-boilerplate (quote (
				      "/*"
				      " * Copyright (c) 2000-2003 INSciTE.  All rights reserved"
				      " * INSciTE is on the web at: http://www.hightechkids.org"
				      " * This code is released under GPL; see LICENSE.txt for details."
				      " */"
                                      )))
 '(jde-gen-class-buffer-template 
   (list 
    "(funcall jde-gen-boilerplate-function)"
    "(jde-gen-get-package-statement)"
    "'>\"import org.apache.log4j.Logger;\"'n"
    "\"/**\" '>'n"
    "\" * Add class comment here!\" '>'n"
    "\" *\" '>'n"
    "\" * @version $Revision$\" '>'n"
    "\" */\" '>'n'"
    "\"public class \"" 
    "(file-name-sans-extension (file-name-nondirectory buffer-file-name))" 
    "\" \" (jde-gen-get-extend-class)" 
    "\"{\" '>'n"
    "'>'n"
    "'>\"private static final Logger LOG = Logger.getLogger(\"(file-name-sans-extension (file-name-nondirectory buffer-file-name))\".class);\"'n"
    "'>'n"
    "\"public \"" 
    "(file-name-sans-extension (file-name-nondirectory buffer-file-name))" 
    "\"()\"" 
    "\" {\" '>'n" 
    "'>'n" 
    "\"}\" '>'n" 
    "\"}\" '>'n")) 
   ))
