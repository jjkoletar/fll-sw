/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;

import org.apache.log4j.Logger;

/**
 * Some handy utilties.
 * 
 * @version $Revision$
 */
public final class Utilities {

  private static final Logger LOG = Logger.getLogger(Utilities.class);
  
  /**
   * Single instance of the default NumberFormat instance to save on overhead
   */
  public static final NumberFormat NUMBER_FORMAT_INSTANCE = NumberFormat.getInstance();
  
  private Utilities() {
  }

  /**
   * Check to see if the database files can be written to.  Will check each of
   * the files that HSQLDB uses for the database and ensure they're all
   * readable and writable.  If there are any problems an exception will be
   * thrown.
   */
  public static void testHSQLDB(final String baseFilename) {
    final File baseFile = new File(baseFilename);
    final File dir = baseFile.getParentFile();
    if(!dir.isDirectory()) {
      throw new RuntimeException("Database directory " + dir.getAbsolutePath() + " is not a directory");
    }
    if(!dir.canWrite()) {
      throw new RuntimeException("Database directory " + dir.getAbsolutePath() + " is not writable");
    }
    if(!dir.canRead()) {
      throw new RuntimeException("Database directory " + dir.getAbsolutePath() + " is not readable");
    }      
    
    final String[] extensions = new String[] {
      ".properties",
      ".script",
      ".log",
      ".data",
      ".backup",
    };
    for(String extension : extensions) {
      final File file = new File(baseFilename + extension);
      if(file.exists() && !file.canWrite()) {
        throw new RuntimeException("Database file " + file.getAbsolutePath() + " exists and is not writable");
      }
      if(file.exists() && !file.canRead()) {
        throw new RuntimeException("Database file " + file.getAbsolutePath() + " exists and is not readable");
      }
    }
    
  }

  /**
   * @return the URL to use to connect to the database.
   */
  public static String getDBURLString(final String hostname,
                                      final String database) {
    if(fll.xml.GenerateDB.USING_HSQLDB) {
      // do this when getting the URL so it gets called from the JSP's as well
      testHSQLDB(database);
      LOG.debug("URL: jdbc:hsqldb:file:" + database + ";shutdown=true");
      return "jdbc:hsqldb:file:" + database + ";shutdown=true";
    } else {
      return "jdbc:mysql://" + hostname + "/" + database + "?user=fll&password=fll&autoReconnect=true";
    }
  }

  /**
   * Get the name of the database driver class.
   */
  public static String getDBDriverName() {
    if(fll.xml.GenerateDB.USING_HSQLDB) {
      return "org.hsqldb.jdbcDriver";
    } else {
      return "org.gjt.mm.mysql.Driver";
    }
  }
  
  /**
   * Calls createDBConnection with fll as username and password
   *
   * @see #createDBConnection(String, String, String)
   */
  public static Connection createDBConnection(final String hostname) throws RuntimeException {
    return createDBConnection(hostname, "fll", "fll");
  }
  
  /**
   * Calls createDBConnection with fll as the database
   *
   * @see #createDBConnection(String, String, String, String)
   * @throws RuntimeException on an error
   */
  public static Connection createDBConnection(final String hostname,
                                              final String username,
                                              final String password)
    throws RuntimeException {
    return createDBConnection(hostname, username, password, "fll");
  }
  
  /**
   * Creates a database connection.
   *
   * @param hostname name of machine to connect to
   * @param username username to use
   * @param password password to use
   * @param database name of the database to connect to
   * @throws RuntimeException on an error
   */
  public static Connection createDBConnection(final String hostname,
                                              final String username,
                                              final String password,
                                              final String database)
    throws RuntimeException {
    //create connection to database and puke if anything goes wrong
    //register the driver
    try{
      Class.forName(getDBDriverName()).newInstance();
    } catch(final ClassNotFoundException e){
      throw new RuntimeException("Unable to load driver.", e);
    } catch(final InstantiationException ie) {
      throw new RuntimeException("Unable to load driver.", ie);
    } catch(final IllegalAccessException iae) {
      throw new RuntimeException("Unable to load driver.", iae);
    }

    Connection connection = null;
    final String myURL;
    if(fll.xml.GenerateDB.USING_HSQLDB) {
      myURL = "jdbc:hsqldb:file:" + database + ";shutdown=true";
    } else {
      myURL = "jdbc:mysql://" + hostname + "/" + database + "?user=" + username + "&password=" + password + "&autoReconnect=true";
    }
    LOG.debug("myURL: " + myURL);
    try {
      connection = DriverManager.getConnection(myURL);
    } catch(final SQLException sqle) {
      throw new RuntimeException("Unable to create connection: " + sqle.getMessage()
                                 + " host: " + hostname
                                 + " database: " + database
                                 + " user: " + username);
    }
    return connection;
  }

  /**
   * Close stmt and ignore SQLExceptions.  This is useful in a finally so that
   * all of the finally block gets executed.  Handles null.
   */
  public static void closeStatement(final Statement stmt) {
    try {
      if(null != stmt) {
        stmt.close();
      }
    } catch(final SQLException sqle) {
      //ignore
      LOG.debug(sqle);
    }
  }

  /**
   * Close prep and ignore SQLExceptions.  This is useful in a finally so that
   * all of the finally block gets executed.  Handles null.
   */
  public static void closePreparedStatement(final PreparedStatement prep) {
    try {
      if(null != prep) {
        prep.close();
      }
    } catch(final SQLException sqle) {
      //ignore
      LOG.debug(sqle);
    }
  }
  
  /**
   * Close rs and ignore SQLExceptions.  This is useful in a finally so that
   * all of the finally block gets executed.  Handles null.
   */
  public static void closeResultSet(final ResultSet rs) {
    try {
      if(null != rs) {
        rs.close();
      }
    } catch(final SQLException sqle) {
      //ignore
      LOG.debug(sqle);
    }
  }

  /**
   * Close connection and ignore SQLExceptions.  This is useful in a finally
   * so that all of the finally block gets executed.  Handles null.
   */
  public static void closeConnection(final Connection connection) {
    try {
      if(null != connection) {
        connection.close();
      }
    } catch(final SQLException sqle) {
      //ignore
      LOG.debug(sqle);
    }
  }

  /**
   * Equals call that handles null without a NullPointerException.
   */
  public static boolean safeEquals(final Object o1,
                                   final Object o2) {
    if(o1 == o2) {
      return true;
    } else if(null == o1 && null != o2) {
      return false;
    } else {
      return o1.equals(o2);
    }
  }
}
