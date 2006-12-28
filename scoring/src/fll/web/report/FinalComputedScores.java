/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.report;

import fll.db.Queries;
import fll.Utilities;

import java.io.IOException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.text.NumberFormat;
import java.text.ParseException;

import java.util.Iterator;

import javax.servlet.jsp.JspWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * Code for finalComputedScores.jsp
 *
 * @version $Revision:300 $
 */
public final class FinalComputedScores {

  private FinalComputedScores() {
     
  }

  /**
   * Generate the actual report.
   */
  public static void generateReport(final String tournament,
                                    final Document document,
                                    final Connection connection,
                                    final JspWriter out) throws SQLException, IOException {

    out.println("<h1>FLL Final Scores for " + tournament + "</h1>");

    out.println("<hr>");
    
    Statement stmt = null;
    ResultSet rawScoreRS = null;
    ResultSet teamsRS = null;
    ResultSet scaledScoreRS = null;
    PreparedStatement prep = null;
    try {
      final Element rootElement = document.getDocumentElement();
      final NodeList subjectiveCategories = rootElement.getElementsByTagName("subjectiveCategory");
      stmt = connection.createStatement();
      
      final Iterator divisionIter = Queries.getDivisions(connection).iterator();
      while(divisionIter.hasNext()) {
        final String division = (String)divisionIter.next();
      
        out.println("<h2>Division: " + division + "</h2>");
      
        out.println("<table border='0'>");
        out.println("  <tr>");
        out.println("    <th>Team # / Organization / Team Name<br>Weight</th>");
        out.println("    <th>&nbsp;</th>");
        for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
          final Element catElement = (Element)subjectiveCategories.item(cat);
          final double catWeight = Utilities.NUMBER_FORMAT_INSTANCE.parse(catElement.getAttribute("weight")).doubleValue();
          if(catWeight > 0.0) {
            final String catTitle = catElement.getAttribute("title");

            out.println("    <th align='center'>" + catTitle + "<br>" + catWeight + "</th>");
          }
        }

        final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
        final double perfWeight = Utilities.NUMBER_FORMAT_INSTANCE.parse(performanceElement.getAttribute("weight")).doubleValue();
        out.println("    <th align='center'>Performance<br>" + perfWeight + "</th>");

        out.println("    <th align='center'>Overall Score</th>");
        out.println("  </tr>");
        out.println("  <tr><td colspan='" + (subjectiveCategories.getLength() + 4) + "'><hr></td></tr>");
        
        prep = connection.prepareStatement("SELECT Teams.Organization,Teams.TeamName,Teams.TeamNumber,FinalScores.OverallScore"
                                         + " FROM Teams,FinalScores,TournamentTeams"
                                         + " WHERE FinalScores.TeamNumber = Teams.TeamNumber"
                                         + " AND TournamentTeams.TeamNumber = Teams.TeamNumber"
                                         + " AND FinalScores.Tournament = ?"
                                         + " AND TournamentTeams.event_division = ?"
                                         + " ORDER BY FinalScores.OverallScore DESC, Teams.TeamNumber");
        prep.setString(1, tournament);
        prep.setString(2, division);
        teamsRS = prep.executeQuery();
        while(teamsRS.next()) {
          final int teamNumber = teamsRS.getInt(3);
          final String organization = teamsRS.getString(1);
          final String teamName = teamsRS.getString(2);

          final double totalScore;
          final double ts = teamsRS.getDouble(4);
          if(teamsRS.wasNull()) {
            totalScore = Double.NaN;
          } else {
            totalScore = ts;
          }
          
          //raw scores
          out.println("  <tr>");
          out.println("    <td>" + teamNumber + " " + organization + "</td>");
          out.println("    <td align='right'>Raw: </td>");

          //subjective categories
          for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
            final Element catElement = (Element)subjectiveCategories.item(cat);
            final double catWeight = Utilities.NUMBER_FORMAT_INSTANCE.parse(catElement.getAttribute("weight")).doubleValue();
            if(catWeight > 0.0) {
              final String catName = catElement.getAttribute("name");
              rawScoreRS = stmt.executeQuery("SELECT RawScore FROM SummarizedScores WHERE TeamNumber = " + teamNumber + " AND Category = '" + catName + "' AND Tournament = '" + tournament + "'");
              final double rawScore;
              if(rawScoreRS.next()) {
                final double v = rawScoreRS.getDouble(1);
                if(rawScoreRS.wasNull()) {
                  rawScore = Double.NaN;
                } else {
                  rawScore = v;
                }
              } else {
                rawScore = Double.NaN;
              }
              out.println("    <td align='center'" + (Double.isNaN(rawScore) ? " class=warn>No Score" : ">" + SCORE_FORMAT.format(rawScore)) + "</td>");
              rawScoreRS.close();
            }
          }

          //performance
          rawScoreRS = stmt.executeQuery("SELECT RawScore FROM SummarizedScores WHERE TeamNumber = " + teamNumber + " AND Category = 'Performance' AND Tournament = '" + tournament + "'");
          final double rawScore;
          if(rawScoreRS.next()) {
            final double v = rawScoreRS.getDouble(1);
            if(rawScoreRS.wasNull()) {
              rawScore = Double.NaN;
            } else {
              rawScore = v;
            }
          } else {
            rawScore = Double.NaN;
          }
          out.println("    <td align='center'" + (Double.isNaN(rawScore) ? " class=warn>No Score" : ">" + SCORE_FORMAT.format(rawScore)) + "</td>");
          rawScoreRS.close();
          out.println("    <td>&nbsp;</td>");
          out.println("  </tr>");

          //scaled scores
          out.println("  <tr>");
          out.println("    <td>" + teamName + "</td>");
          out.println("    <td align='right'>Scaled: </td>");

          //subjective categories
          for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
            final Element catElement = (Element)subjectiveCategories.item(cat);
            final double catWeight = Utilities.NUMBER_FORMAT_INSTANCE.parse(catElement.getAttribute("weight")).doubleValue();
            if(catWeight > 0.0) {
              final String catName = catElement.getAttribute("name");
              scaledScoreRS = stmt.executeQuery("SELECT StandardizedScore FROM SummarizedScores WHERE TeamNumber = " + teamNumber + " AND Category = '" + catName + "' AND Tournament = '" + tournament + "'");
              final double scaledScore;
              if(scaledScoreRS.next()) {
                final double v = scaledScoreRS.getDouble(1);
                if(scaledScoreRS.wasNull()) {
                  scaledScore = Double.NaN;
                } else {
                  scaledScore = v;
                }
              } else {
                scaledScore = Double.NaN;
              }

              out.println("    <td align='center'" + (Double.isNaN(scaledScore) ? " class=warn>No Score" : ">" + SCORE_FORMAT.format(scaledScore)) + "</td>");

              scaledScoreRS.close();
            }
          }

          //performance
          {
            scaledScoreRS = stmt.executeQuery("SELECT StandardizedScore FROM SummarizedScores WHERE TeamNumber = " + teamNumber + " AND Category = 'Performance' AND Tournament = '" + tournament + "'");
            final double scaledScore;
            if(scaledScoreRS.next()) {
              final double v = scaledScoreRS.getDouble(1);
              if(scaledScoreRS.wasNull()) {
                scaledScore = Double.NaN;
              } else {
                scaledScore = v;
              }
            } else {
              scaledScore = Double.NaN;
            }

            out.println("    <td align='center'" + (Double.isNaN(scaledScore) ? " class=warn>No Score" :  ">" + SCORE_FORMAT.format(scaledScore)) + "</td>");
          }
          
          scaledScoreRS.close();

          //total score
          out.println("    <td align='center'" + (Double.isNaN(totalScore) ? " class=warn>No Score" : ">" + SCORE_FORMAT.format(totalScore)) + "</td>");

          out.println("  <tr><td colspan='" + (subjectiveCategories.getLength() + 4) + "'><hr></td></tr>");
        }
        
        out.println("</table>");
        teamsRS.close();

      } //end while(divisionIter.next())

    } catch(final ParseException pe) {
      throw new RuntimeException("Error parsing category weight!", pe);
    } finally {
      Utilities.closeResultSet(rawScoreRS);
      Utilities.closeResultSet(teamsRS);
      Utilities.closeResultSet(scaledScoreRS);
      
      Utilities.closeStatement(stmt);
    }
  }

  private static final NumberFormat SCORE_FORMAT = NumberFormat.getInstance();
  static {
    SCORE_FORMAT.setMaximumFractionDigits(2);
    SCORE_FORMAT.setMinimumFractionDigits(2);
  }    

}
