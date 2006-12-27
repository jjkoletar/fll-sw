/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fll.Utilities;

/**
 * Import scores from a tournament database into a master score database.
 *
 * <p>Example arguments: jdbc:hsqldb:file:/source;shutdown=true "Centennial Dec10" jdbc:hsqldb:file:/destination;shutdown=true
 *
 * @version $Revision$
 */
public final class ImportDB {

  private static final Logger LOG = Logger.getLogger(ImportDB.class);
  
  public static void main(final String[] args) {
    try {
      if(args.length != 3) {
        LOG.error("You must specify <source uri> <tournament> <destination uri>");
        System.exit(1);
      } else {
        
        final String sourceURI = args[0];
        //remove quotes from tournament if they exist
        int substringStart = 0;
        int substringEnd = args[1].length();
        if(args[1].charAt(0) == '"' || args[1].charAt(0) == '\'') {
          substringStart = 1;
        }
        if(args[1].charAt(substringEnd-1) == '"' || args[1].charAt(substringEnd-1) == '\'') {
          substringEnd = substringEnd-1;
        }
        final String tournament = args[1].substring(substringStart, substringEnd);
        final String destinationURI = args[2];

        try {
          Class.forName("org.hsqldb.jdbcDriver").newInstance();
        } catch(final ClassNotFoundException e){
          throw new RuntimeException("Unable to load driver.", e);
        } catch(final InstantiationException ie) {
          throw new RuntimeException("Unable to load driver.", ie);
        } catch(final IllegalAccessException iae) {
          throw new RuntimeException("Unable to load driver.", iae);
        }

        try {
          Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch(final ClassNotFoundException e){
          LOG.warn("Unable to load driver.", e);
        } catch(final InstantiationException ie) {
          LOG.warn("Unable to load driver.", ie);
        } catch(final IllegalAccessException iae) {
          LOG.warn("Unable to load driver.", iae);
        }
        
        final Connection sourceConnection = DriverManager.getConnection(sourceURI);
        final Connection destinationConnection = DriverManager.getConnection(destinationURI);
        Statement stmt1 = null;
        Statement stmt2 = null;
        try {
          try {
            stmt1 = sourceConnection.createStatement();
            stmt1.executeUpdate("SET WRITE_DELAY 1 MILLIS");
          } catch(final SQLException sqle) {
            LOG.info("Source either isn't HSQLDB or there is a problem", sqle);
          }
          try {
            stmt2 = destinationConnection.createStatement();
            stmt2.executeUpdate("SET WRITE_DELAY 1 MILLIS");
          } catch(final SQLException sqle) {
            LOG.info("Destination either isn't HSQLDB or there is a problem", sqle);
          }          
        } finally {
          Utilities.closeStatement(stmt1);
          Utilities.closeStatement(stmt2);
        }

        
        final boolean differences = checkForDifferences(sourceConnection, destinationConnection, tournament);
        if(!differences) {
          LOG.info("Importing data for " + tournament + " from " + sourceURI + " to " + destinationURI);
          final Document challengeDocument = Queries.getChallengeDocument(destinationConnection);
          importDatabase(sourceConnection, destinationConnection, tournament, challengeDocument);
          LOG.info("Data successfully imported");
        } else {
          LOG.error("Import aborted due to differences in databases");
        }

        try {
          try {
            stmt1 = sourceConnection.createStatement();
            stmt1.executeUpdate("SHUTDOWN COMPACT");
          } catch(final SQLException sqle) {
            LOG.info("Source either isn't HSQLDB or there is a problem", sqle);
          }
          try {
            stmt2 = destinationConnection.createStatement();
            stmt2.executeUpdate("SHUTDOWN COMPACT");
          } catch(final SQLException sqle) {
            LOG.info("Destination either isn't HSQLDB or there is a problem", sqle);
          }          
        } finally {
          Utilities.closeStatement(stmt1);
          Utilities.closeStatement(stmt2);
        }
        
      }
    } catch(final Exception e) {
      e.printStackTrace();
    }
  }
  
  private ImportDB() {
    // no instances
  }

  /**
   * Import scores from database for tournament into the database for
   * connection.
   *
   * @param sourceConnection a connection to the source database
   * @param destinationConnection a connection to the destination database
   * @param tournament the tournament that the scores are for
   * @param document the XML document that describes the tournament
   */
  public static void importDatabase(final Connection sourceConnection,
                                    final Connection destinationConnection,
                                    final String tournament,
                                    final Document document)
    throws SQLException {

    PreparedStatement destPrep = null;
    PreparedStatement sourcePrep = null;
    ResultSet sourceRS = null;
    try {
      final Element rootElement = document.getDocumentElement();
      
      //judges
      LOG.info("Importing Judges");
      destPrep = destinationConnection.prepareStatement("DELETE FROM Judges WHERE Tournament = ?");
      destPrep.setString(1, tournament);
      destPrep.executeUpdate();
      Utilities.closePreparedStatement(destPrep);

      destPrep = destinationConnection.prepareStatement("INSERT INTO Judges (id, category, event_division, Tournament) VALUES (?, ?, ?, ?)");
      destPrep.setString(4, tournament);

      sourcePrep = sourceConnection.prepareStatement("SELECT id, category, event_division FROM Judges WHERE Tournament = ?");
      sourcePrep.setString(1, tournament);
      sourceRS = sourcePrep.executeQuery();
      while(sourceRS.next()) {
        destPrep.setString(1, sourceRS.getString(1));
        destPrep.setString(2, sourceRS.getString(2));
        destPrep.setString(3, sourceRS.getString(3));
        destPrep.executeUpdate();
      }
      Utilities.closeResultSet(sourceRS);
      Utilities.closePreparedStatement(sourcePrep);
      Utilities.closePreparedStatement(destPrep);
      
      // import tournament teams
      LOG.info("Importing TournamentTeams");
      destPrep = destinationConnection.prepareStatement("DELETE FROM TournamentTeams WHERE Tournament = ?");
      destPrep.setString(1, tournament);
      destPrep.executeUpdate();
      Utilities.closePreparedStatement(destPrep);
      sourcePrep = sourceConnection.prepareStatement("SELECT TeamNumber, event_division, advanced FROM TournamentTeams WHERE Tournament = ?");
      sourcePrep.setString(1, tournament);
      destPrep = destinationConnection.prepareStatement("INSERT INTO TournamentTeams (Tournament, TeamNumber, event_division, advanced) VALUeS (?, ?, ?, ?)");
      destPrep.setString(1, tournament);
      sourceRS = sourcePrep.executeQuery();
      while(sourceRS.next()) {
        destPrep.setInt(2, sourceRS.getInt(1));
        destPrep.setString(3, sourceRS.getString(2));
        destPrep.setBoolean(4, sourceRS.getBoolean(3));
        destPrep.executeUpdate();
      }
      Utilities.closeResultSet(sourceRS);
      Utilities.closePreparedStatement(sourcePrep);
      Utilities.closePreparedStatement(destPrep);
      
      //performance
      {
        LOG.info("Importing performance scores");
        final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
        final String tableName = "Performance";
        destPrep = destinationConnection.prepareStatement("DELETE FROM " + tableName + " WHERE Tournament = ?");
        destPrep.setString(1, tournament);
        destPrep.executeUpdate();
        Utilities.closePreparedStatement(destPrep);
        
        final StringBuffer columns = new StringBuffer();
        columns.append(" TeamNumber,");
        columns.append(" Tournament,");
        columns.append(" RunNumber,");
        columns.append(" TimeStamp,");
        final NodeList goals = performanceElement.getElementsByTagName("goal");
        final int numColumns = goals.getLength() + 6;
        for(int i=0; i<goals.getLength(); i++) {
          final Element element = (Element)goals.item(i);
          columns.append(" " + element.getAttribute("name") + ",");
        }
        columns.append(" NoShow,");
        columns.append(" Bye");

        StringBuffer sql = new StringBuffer();
        sql.append("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");
        sql.append(columns.toString());
        sql.append(") VALUES (");
        for(int i=0; i<numColumns; i++) {
          if(i > 0) {
            sql.append(", ");
          }
          sql.append("?");
        }
        sql.append(")");
        destPrep = destinationConnection.prepareStatement(sql.toString());

        sourcePrep = sourceConnection.prepareStatement("SELECT " + columns.toString() + " FROM " + tableName + " WHERE Tournament = ?");
        sourcePrep.setString(1, tournament);
        sourceRS = sourcePrep.executeQuery();
        while(sourceRS.next()) {
          for(int i=0; i<numColumns; i++) {
            destPrep.setObject(i+1, sourceRS.getObject(i+1));
          }
          destPrep.executeUpdate();
        }
        Utilities.closeResultSet(sourceRS);
        Utilities.closePreparedStatement(sourcePrep);
        Utilities.closePreparedStatement(destPrep);
      }
      
      //loop over each subjective category
      final NodeList subjectiveCategories = rootElement.getElementsByTagName("subjectiveCategory");
      for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
        final Element categoryElement = (Element)subjectiveCategories.item(cat);
        final String tableName = categoryElement.getAttribute("name");
        LOG.info("Importing " + tableName);
        
        destPrep = destinationConnection.prepareStatement("DELETE FROM " + tableName + " WHERE Tournament = ?");
        destPrep.setString(1, tournament);
        destPrep.executeUpdate();
        Utilities.closePreparedStatement(destPrep);
        
        final StringBuffer columns = new StringBuffer();
        columns.append(" TeamNumber,");
        columns.append(" Tournament,");
        final NodeList goals = categoryElement.getElementsByTagName("goal");
        final int numColumns = goals.getLength() + 3;
        for(int i=0; i<goals.getLength(); i++) {
          final Element element = (Element)goals.item(i);
          columns.append(" " + element.getAttribute("name") + ",");
        }
        columns.append(" Judge");

        StringBuffer sql = new StringBuffer();
        sql.append("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");
        sql.append(columns.toString());
        sql.append(") VALUES (");
        for(int i=0; i<numColumns; i++) {
          if(i > 0) {
            sql.append(", ");
          }
          sql.append("?");
        }
        sql.append(")");
        destPrep = destinationConnection.prepareStatement(sql.toString());

        sourcePrep = sourceConnection.prepareStatement("SELECT " + columns.toString() + " FROM " + tableName + " WHERE Tournament = ?");
        sourcePrep.setString(1, tournament);
        sourceRS = sourcePrep.executeQuery();
        while(sourceRS.next()) {
          for(int i=0; i<numColumns; i++) {
            destPrep.setObject(i+1, sourceRS.getObject(i+1));
          }
          destPrep.executeUpdate();
        }
        Utilities.closeResultSet(sourceRS);
        Utilities.closePreparedStatement(sourcePrep);
        Utilities.closePreparedStatement(destPrep);
      }

      // PlayoffData
      {
        LOG.info("Importing PlayoffData");
        destPrep = destinationConnection.prepareStatement("DELETE FROM PlayoffData WHERE Tournament = ?");
        destPrep.setString(1, tournament);
        destPrep.executeUpdate();
        Utilities.closePreparedStatement(destPrep);

        sourcePrep = sourceConnection.prepareStatement("SELECT event_division, Tournament, PlayoffRound, LineNumber, Team, AssignedTable, Printed " +
            "FROM PlayoffData WHERE Tournament=?");
        sourcePrep.setString(1, tournament);
        destPrep = destinationConnection.prepareStatement("INSERT INTO PlayoffData (event_division, Tournament, PlayoffRound," +
            "LineNumber, Team, AssignedTable, Printed) VALUES (?, ?, ?, ?, ?, ?, ?)");
        sourceRS = sourcePrep.executeQuery();
        while(sourceRS.next()) {
          for(int i = 1; i < 8; i++) {
            destPrep.setObject(i, sourceRS.getObject(i));
          }
          destPrep.executeUpdate();
        }
        Utilities.closeResultSet(sourceRS);
        Utilities.closePreparedStatement(sourcePrep);
        Utilities.closePreparedStatement(destPrep);
      }

      // TableNames
      {
        LOG.info("Importing tablenames");
        destPrep = destinationConnection.prepareStatement("DELETE FROM tablenames WHERE Tournament = ?");
        destPrep.setString(1, tournament);
        destPrep.executeUpdate();
        Utilities.closePreparedStatement(destPrep);

        sourcePrep = sourceConnection.prepareStatement("SELECT Tournament, SideA, SideB " +
            "FROM tablenames WHERE Tournament=?");
        sourcePrep.setString(1, tournament);
        destPrep = destinationConnection.prepareStatement("INSERT INTO tablenames (Tournament, SideA, SideB) " +
            "VALUES (?, ?, ?)");
        sourceRS = sourcePrep.executeQuery();
        while(sourceRS.next()) {
          for(int i = 1; i < 4; i++) {
            destPrep.setObject(i, sourceRS.getObject(i));
          }
          destPrep.executeUpdate();
        }
        Utilities.closeResultSet(sourceRS);
        Utilities.closePreparedStatement(sourcePrep);
        Utilities.closePreparedStatement(destPrep);
      }
    } finally {
      Utilities.closeResultSet(sourceRS);
      Utilities.closePreparedStatement(sourcePrep);
      Utilities.closePreparedStatement(destPrep);
    }
  }

  /**
   * Check for differences between two tournaments in team information.
   *
   * @return true if there are differences
   */
  public static boolean checkForDifferences(final Connection sourceConnection,
                                            final Connection destinationConnection,
                                            final String tournament)
    throws SQLException {
    PreparedStatement sourcePrep = null;
    PreparedStatement destPrep = null;
    ResultSet sourceRS = null;
    ResultSet destRS = null;
    boolean differencesFound = false;
    try {
      // check that the tournament exists
      destPrep = destinationConnection.prepareStatement("SELECT Name FROM Tournaments WHERE Name = ?");
      destPrep.setString(1, tournament);
      if(!destPrep.executeQuery().next()) {
        LOG.error("Tournament: " + tournament + " doesn't exist in the destination database!");
        return true;
      }
      Utilities.closePreparedStatement(destPrep);

      sourcePrep = sourceConnection.prepareStatement("SELECT Name FROM Tournaments WHERE Name = ?");
      sourcePrep.setString(1, tournament);
      if(!sourcePrep.executeQuery().next()) {
        LOG.error("Tournament: " + tournament + " doesn't exist in the source database!");
        return true;
      }
      Utilities.closePreparedStatement(sourcePrep);
      
      // check names and regions and make sure that each team in the source
      // tournament is in the destination tournament
      destPrep = destinationConnection.prepareStatement("SELECT Teams.TeamName, Teams.Region, Teams.Division, Teams.Organization"
           + " FROM Teams"
           + " WHERE Teams.TeamNumber = ?");

      sourcePrep = sourceConnection.prepareStatement("SELECT Teams.TeamNumber, Teams.TeamName, Teams.Region, Teams.Division, Teams.Organization"
          + " FROM Teams, TournamentTeams"
          + " WHERE Teams.TeamNumber = TournamentTeams.TeamNumber"
          + " AND TournamentTeams.Tournament = ?");
      
      sourcePrep.setString(1, tournament);
      sourceRS = sourcePrep.executeQuery();
      while(sourceRS.next()) {
        final int num = sourceRS.getInt(1);
        final String sourceName = sourceRS.getString(2);
        final String sourceRegion = sourceRS.getString(3);
        final String sourceDivision= sourceRS.getString(4);
        final String sourceOrganization = sourceRS.getString(5);
        destPrep.setInt(1, num);
        destRS = destPrep.executeQuery();
        if(destRS.next()) {
          final String destName = destRS.getString(1);
          if(!Utilities.safeEquals(destName, sourceName)) {
            differencesFound = true;
            LOG.error("There is a team with a different name in the source database that in the destination database.  Number: " + num + " source name: " + sourceName + " dest name: " + destName);
          } 
          final String destRegion = destRS.getString(2);
          if(!Utilities.safeEquals(destRegion, sourceRegion)) {
            differencesFound = true;
            LOG.error("There is a team with a different region in the source database that in the destination database.  Number: " + num + " source region: " + sourceRegion + " dest region: " + destRegion);
          }
          final String destDivision = destRS.getString(3);
          if(!Utilities.safeEquals(destDivision, sourceDivision)) {
            differencesFound = true;
            LOG.error("There is a team with a different division in the source database that in the destination database.  Number: " + num + " source division: " + sourceDivision + " dest division: " + destDivision);
          }
          final String destOrganization = destRS.getString(4);
          if(!Utilities.safeEquals(destOrganization, sourceOrganization)) {
            differencesFound = true;
            LOG.error("There is a team with a different organization in the source database that in the destination database.  Number: " + num + " source organization: " + sourceOrganization+ " dest organization: " + destOrganization);
          }

        } else {
          differencesFound = true;
          LOG.error("There is a team in the source database that isn't in the destination database. Number: " + num + " name: " + sourceName);
        }
        Utilities.closeResultSet(destRS);
      }
      Utilities.closeResultSet(destRS);
      Utilities.closeResultSet(sourceRS);
      Utilities.closePreparedStatement(destPrep);
      Utilities.closePreparedStatement(sourcePrep);
      
      //FIX check documents
      
    } finally {
      Utilities.closeResultSet(sourceRS);
      Utilities.closeResultSet(destRS);
      Utilities.closePreparedStatement(sourcePrep);
      Utilities.closePreparedStatement(destPrep);
    }
    return differencesFound;
  }
  
}