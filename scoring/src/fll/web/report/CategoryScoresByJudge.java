/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.report;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.mtu.eggplant.util.sql.SQLFunctions;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fll.Utilities;
import fll.db.Queries;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.SessionAttributes;
import fll.xml.WinnerType;
import fll.xml.XMLUtils;

/**
 * Display the report for scores by score group.
 * 
 * @author jpschewe
 * @version $Revision$
 */
public class CategoryScoresByJudge extends BaseFLLServlet {

  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session) throws IOException, ServletException {

    final DataSource datasource = (DataSource) session.getAttribute(SessionAttributes.DATASOURCE);
    final Document challengeDocument = ApplicationAttributes.getChallengeDocument(application);

    final WinnerType winnerCriteria = XMLUtils.getWinnerCriteria(challengeDocument);
    final String ascDesc = WinnerType.HIGH == winnerCriteria ? "DESC" : "ASC";

    final PrintWriter writer = response.getWriter();
    writer.write("<html><body>");
    writer.write("<h1>FLL Categorized Score Summary by judge</h1>");
    writer.write("<hr/>");

    // cache the subjective categories title->dbname
    final Map<String, String> subjectiveCategories = new HashMap<String, String>();
    for (final Element subjectiveElement : XMLUtils.filterToElements(challengeDocument.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
      final String title = subjectiveElement.getAttribute("title");
      final String name = subjectiveElement.getAttribute("name");
      subjectiveCategories.put(title, name);
    }

    ResultSet rs = null;
    PreparedStatement prep = null;
    PreparedStatement judgesPrep = null;
    ResultSet judgesRS = null;
    try {
      final Connection connection = datasource.getConnection();

      final int currentTournament = Queries.getCurrentTournament(connection);

      // foreach division
      for (final String division : Queries.getEventDivisions(connection)) {
        
        // foreach subjective category
        for(final Map.Entry<String, String> entry : subjectiveCategories.entrySet()) {
          final String categoryTitle = entry.getKey();
          final String categoryName = entry.getValue();

          judgesPrep = connection.prepareStatement("SELECT DISTINCT " + categoryName + ".Judge"//
                                                  + " FROM " + categoryName + ",current_tournament_teams"//
                                                  + " WHERE " + categoryName + ".TeamNumber = current_tournament_teams.TeamNumber"
                                                  + " AND " + categoryName + ".Tournament = ?"
                                                  + " AND current_tournament_teams.event_division = ?");          
          judgesPrep.setInt(1, currentTournament);
          judgesPrep.setString(2, division);
          judgesRS = judgesPrep.executeQuery();
                    
          // select from FinalScores
          while(judgesRS.next()) {
            final String judge = judgesRS.getString(1);
            
            writer.write("<h3>"
                + categoryTitle + " Division: " + division + " Judge: " + judge+ "</h3>");
            
            writer.write("<table border='0'>");
            writer.write("<tr><th colspan='3'>Team # / Organization / Team Name</th><th>Raw Score</th><th>Scaled Score</th></tr>");
            
            prep = connection.prepareStatement("SELECT"//
                +" Teams.TeamNumber"//
                + ",Teams.Organization"//
                + ",Teams.TeamName"//
                + "," + categoryName + ".ComputedTotal"//
                + "," + categoryName + ".StandardizedScore"//
                + " FROM Teams, " + categoryName//
                + " WHERE Teams.TeamNumber = " + categoryName + ".TeamNumber"//
                + " AND Tournament = ?"//
                + " AND Judge = ?"//
                + " AND " + categoryName + ".ComputedTotal IS NOT NULL"//
                + " ORDER BY " + categoryName + ".ComputedTotal " + ascDesc // get best score first
                                               ); 
            prep.setInt(1, currentTournament);
            prep.setString(2, judge);
            rs = prep.executeQuery();
            while (rs.next()) {
              final int teamNum = rs.getInt(1);
              final String org = rs.getString(2);
              final String name = rs.getString(3);
              final double score = rs.getDouble(4);
              final boolean scoreWasNull = rs.wasNull();
              final double rawScore = rs.getDouble(5);
              final boolean rawScoreWasNull = rs.wasNull();
              
              writer.write("<tr>");
              writer.write("<td>");
              writer.write(teamNum);
              writer.write("</td>");
              writer.write("<td>");
              if (null == org) {
                writer.write("");
              } else {
                writer.write(org);
              }
              writer.write("</td>");
              writer.write("<td>");
              if (null == name) {
                writer.write("");
              } else {
                writer.write(name);
              }
              writer.write("</td>");
              
              if (!scoreWasNull) {
                writer.write("<td>");
                writer.write(Utilities.NUMBER_FORMAT_INSTANCE.format(score));
              } else {
                writer.write("<td align='center' class='warn'>No Score");
              }
              writer.write("</td>");
              
              if (!rawScoreWasNull) {
                writer.write("<td>");
                writer.write(Utilities.NUMBER_FORMAT_INSTANCE.format(rawScore));
              } else {
                writer.write("<td align='center' class='warn'>No Score");
              }
              writer.write("</td>");
              
              writer.write("</tr>");
            }// foreach team
            writer.write("<tr><td colspan='5'><hr/></td></tr>");
            writer.write("</table");
            SQLFunctions.closeResultSet(rs);
            SQLFunctions.closePreparedStatement(prep);
          }// foreach judge
          SQLFunctions.closeResultSet(judgesRS);
          SQLFunctions.closePreparedStatement(judgesPrep);
        }// foreach category
        
      }// foreach division

    } catch (final SQLException sqle) {
      throw new RuntimeException(sqle);
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
      SQLFunctions.closeResultSet(judgesRS);
      SQLFunctions.closePreparedStatement(judgesPrep);
    }

    writer.write("</body></html>");
  }
}