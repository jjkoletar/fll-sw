/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.report;

import javax.servlet.jsp.JspWriter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fll.db.Queries;
import fll.Utilities;

import java.text.NumberFormat;

import java.io.IOException;

import java.util.Iterator;


/**
 * Code for categorizedScores.jsp.
 *
 * @version $Revision$
 */
public final class CategoryScores {
   
  private CategoryScores() {
     
  }

  /**
   * Generate the actual report for category scores
   */
  public static void generateReport(final String tournament,
                                    final Document document,
                                    final Connection connection,
                                    final JspWriter out)
    throws SQLException, IOException {

    final NodeList subjectiveCategories = document.getDocumentElement().getElementsByTagName("subjectiveCategory");
      
    out.println("<h1>FLL Categorized Score Summary</h1>");

    out.println("<hr>");

    out.println("<h2>Tournament: " + tournament + "</h2>");

      
    //get the list of divisions
    final Iterator divisionIter = Queries.getDivisions(connection).iterator();
    while(divisionIter.hasNext()) {
      final String division = (String)divisionIter.next();

      //foreach category
      for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
        final Element catElement = (Element)subjectiveCategories.item(cat);
        final String catName = catElement.getAttribute("name");
        final String catTitle = catElement.getAttribute("title"); 

        generateCategoryTable(tournament, connection, out, catName, catTitle, division);
      }

      //performance
      generateCategoryTable(tournament, connection, out, "Performance", "Performance", division);
        
    }
  }

  /**
   * Generate the table for a category
   */
  private static void generateCategoryTable(final String tournament,
                                            final Connection connection,
                                            final JspWriter out,
                                            final String categoryName,
                                            final String categoryTitle,
                                            final String division)
    throws SQLException, IOException {
    
    ResultSet teamsRS = null;
    ResultSet scoreRS = null;
    PreparedStatement prep = null;
    try {

      out.println("<h3>" + categoryTitle + " Division: " + division + "</h3>");
          
      out.println("<table border='0'>");
      out.println("  <tr>");
      out.println("    <th colspan='3'>Team # / Organization / Team Name</th>");
      out.println("    <th>Raw</th>");
      out.println("    <th>Scaled</th>");
      out.println("  </tr>");

      out.println("  <tr><td colspan='5'><hr></td></tr>");
          
      //display all team scores
      prep = connection.prepareStatement("SELECT Teams.Organization,Teams.TeamName,Teams.TeamNumber,SummarizedScores.RawScore,SummarizedScores.StandardizedScore"
                               + " FROM SummarizedScores,Teams,TournamentTeams"
                               + " WHERE Teams.TeamNumber = SummarizedScores.TeamNumber"
                               + " AND TournamentTeams.TeamNumber = Teams.TeamNumber"
                               + " AND SummarizedScores.Tournament = ?"
                               + " AND TournamentTeams.event_division = ?"
                               + " AND SummarizedScores.Category = ?"
                               + " ORDER BY SummarizedScores.StandardizedScore DESC, Teams.TeamNumber");
      prep.setString(1, tournament);
      prep.setString(2, division);
      prep.setString(3, categoryName);
      teamsRS = prep.executeQuery();
      while(teamsRS.next()) {
        final int teamNumber = teamsRS.getInt(3);
        final String organization = teamsRS.getString(1);
        final String teamName = teamsRS.getString(2);

        final double rawScore;
        double v = teamsRS.getDouble(4);
        if(teamsRS.wasNull()) {
          rawScore = Double.NaN;
        } else {
          rawScore = v;
        }

        final double scaledScore;
        v = teamsRS.getDouble(5);
        if(teamsRS.wasNull()) {
          scaledScore = Double.NaN;
        } else {
          scaledScore = v;
        }
        
        out.println("  <tr>");

        out.println("    <td>" + teamNumber + "</td>");
        out.println("    <td>" + organization + "</td>");
        out.println("    <td>" + teamName + "</td>");
            
        out.println("    <td>" + SCORE_FORMAT.format(rawScore) + "</td>");

        //scoreRS.close();

        out.println("    <td>" + SCORE_FORMAT.format(scaledScore) + "</td>");

        //scoreRS.close();
        out.println("  </tr>");
            
        out.println("  <tr><td colspan='5'><hr</td></tr>");
      } //end loop over teams
      teamsRS.close();

      out.println("</table>");
    } finally {
      Utilities.closeResultSet(teamsRS);
      Utilities.closeResultSet(scoreRS);
      Utilities.closePreparedStatement(prep);
    }
  }
  
  private static final NumberFormat SCORE_FORMAT = NumberFormat.getInstance();
  static {
    SCORE_FORMAT.setMaximumFractionDigits(2);
    SCORE_FORMAT.setMinimumFractionDigits(2);
  }    
    
}
