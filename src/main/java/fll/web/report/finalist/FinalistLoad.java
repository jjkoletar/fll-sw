/*
 * Copyright (c) 2012 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web.report.finalist;

import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import fll.Tournament;
import fll.TournamentTeam;
import fll.db.NonNumericNominees;
import fll.db.Queries;
import fll.web.ApplicationAttributes;
import fll.web.WebUtils;
import fll.web.playoff.Playoff;
import fll.web.report.FinalComputedScores;
import fll.xml.ChallengeDescription;
import fll.xml.SubjectiveScoreCategory;
import fll.xml.WinnerType;

/**
 * Support for /report/finalist/load.jsp.
 */
public class FinalistLoad {

  /**
   * The name of the javascript variable that represents the team.
   *
   * @return the variable name to use for the specified team number
   * @param teamNumber the team to get the variable for
   */
  public static String getTeamVarName(final int teamNumber) {
    return "team"
        + teamNumber;
  }

  /**
   * The name of the javascript variable that represents the category.
   *
   * @param categoryName the category to get the variable for
   * @return the variable name to use for the category
   */
  public static String getCategoryVarName(final String categoryName) {
    return categoryName
        + "Category";
  }

  /**
   * Output the variable definition for the specified team.
   *
   * @param output where to write
   * @param team the team
   * @param connection the database connection
   * @throws SQLException if there is a problem talking to the database
   */
  public static void outputTeamVarDefinition(final Formatter output,
                                             final Connection connection,
                                             final TournamentTeam team)
      throws SQLException {
    final String teamVarName = getTeamVarName(team.getTeamNumber());
    output.format("var %s = $.finalist.lookupTeam(%d);%n", teamVarName, team.getTeamNumber());
    output.format("if(null == %s) {%n", teamVarName);
    output.format("  %s = $.finalist.addTeam(%d, %s, %s, %s);%n", teamVarName, team.getTeamNumber(),
                  WebUtils.quoteJavascriptString(team.getTrimmedTeamName()),
                  WebUtils.quoteJavascriptString(team.getOrganization()),
                  WebUtils.quoteJavascriptString(team.getJudgingGroup()));
    output.format("}%n");

    output.format("$.finalist.addTeamToDivision(%s, %s);%n", teamVarName,
                  WebUtils.quoteJavascriptString(team.getAwardGroup()));

    for (final String playoffDivision : Playoff.getPlayoffBracketsForTeam(connection, team.getTeamNumber())) {
      output.format("$.finalist.addTeamToPlayoffDivision(%s, %s);%n", teamVarName,
                    WebUtils.quoteJavascriptString(playoffDivision));
    }
  }

  /**
   * Output the variables for the teams.
   *
   * @param writer where to write
   * @param application the application context
   * @throws SQLException if a database error occurs
   */
  public static void outputTeamVariables(final Writer writer,
                                         final ServletContext application)
      throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    try (Connection connection = datasource.getConnection()) {
      final Formatter output = new Formatter(writer);
      for (final TournamentTeam team : Queries.getTournamentTeams(connection).values()) {
        outputTeamVarDefinition(output, connection, team);
      }
    }
  }

  /**
   * Output the award group information.
   *
   * @param writer where to write
   * @param application the application context
   * @throws SQLException if a database errors occurs
   */
  public static void outputDivisions(final Writer writer,
                                     final ServletContext application)
      throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    try (Connection connection = datasource.getConnection()) {

      final int tournament = Queries.getCurrentTournament(connection);

      final Formatter output = new Formatter(writer);
      for (final String division : Queries.getAwardGroups(connection, tournament)) {
        output.format("$.finalist.addDivision(%s);%n", WebUtils.quoteJavascriptString(division));
      }

      for (final String playoffDivision : Playoff.getPlayoffBrackets(connection, tournament)) {
        output.format("$.finalist.addPlayoffDivision(%s);%n", WebUtils.quoteJavascriptString(playoffDivision));
      }

    }
  }

  /**
   * @param application the application context to get information from
   * @return the current tournament name as a quoted javascript string
   * @throws SQLException on a database error
   */
  public static String currentTournament(final ServletContext application) throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    try (Connection connection = datasource.getConnection()) {
      final String name = Queries.getCurrentTournamentName(connection);
      return WebUtils.quoteJavascriptString(name);
    }
  }

  /**
   * Output the declarations for the category variables.
   *
   * @param writer where to write
   * @param application the application context to get information from
   */
  public static void outputCategories(final Writer writer,
                                      final ServletContext application) {
    final ChallengeDescription description = ApplicationAttributes.getChallengeDescription(application);
    final Formatter output = new Formatter(writer);

    for (final SubjectiveScoreCategory subjectiveElement : description.getSubjectiveCategories()) {
      final String categoryName = subjectiveElement.getName();
      final String categoryTitle = subjectiveElement.getTitle();
      final String quotedCatTitle = WebUtils.quoteJavascriptString(categoryTitle);

      final String catVarName = getCategoryVarName(categoryName);
      output.format("var %s = $.finalist.getCategoryByName(%s);%n", catVarName, quotedCatTitle);
      output.format("if (null == %s) {%n", catVarName);
      output.format("  %s = $.finalist.addCategory(%s, true);%n", catVarName, quotedCatTitle);
      output.format("}%n");
    }
  }

  /**
   * Output javascript to load the subjective nominees into the finalist
   * schedule
   * web application.
   *
   * @param writer where to write
   * @param application the application context
   * @throws SQLException on a database error
   */
  public static void outputNonNumericNominees(final Writer writer,
                                              final ServletContext application)
      throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    try (Connection connection = datasource.getConnection()) {

      final int tournament = Queries.getCurrentTournament(connection);
      final Formatter output = new Formatter(writer);

      final int varIndex = 0;
      for (final String category : NonNumericNominees.getCategories(connection, tournament)) {
        final String categoryVar = "categoryVar"
            + varIndex;

        output.format("var %s = $.finalist.getCategoryByName(\"%s\");%n", categoryVar, category);
        output.format("if (null == %s) {%n", categoryVar);
        output.format("  %s = $.finalist.addCategory(\"%s\", false);%n", categoryVar, category);
        output.format("}%n");

        for (final int teamNumber : NonNumericNominees.getNominees(connection, tournament, category)) {
          output.format("$.finalist.addTeamToCategory(%s, %d);%n", categoryVar, teamNumber);
        }
      }
    }
  }

  /**
   * Output the category scores.
   *
   * @param writer where to write
   * @param application the application context
   * @throws SQLException if a database error occurs
   */
  public static void outputCategoryScores(final Writer writer,
                                          final ServletContext application)
      throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    try (Connection connection = datasource.getConnection()) {

      final ChallengeDescription description = ApplicationAttributes.getChallengeDescription(application);
      final Tournament tournament = Tournament.getCurrentTournament(connection);
      final Formatter output = new Formatter(writer);

      try (
          PreparedStatement prep = connection.prepareStatement("SELECT team_number, overall_score from overall_scores WHERE tournament = ?")) {
        prep.setInt(1, tournament.getTournamentID());

        try (ResultSet rs = prep.executeQuery()) {
          // defined by load.jsp
          final String championshipCategoryVar = "championship";
          while (rs.next()) {
            final int teamNumber = rs.getInt(1);
            final String teamVar = getTeamVarName(teamNumber);

            final double overallScore = rs.getDouble(2);
            output.format("$.finalist.setCategoryScore(%s, %s, %.02f);%n", teamVar, championshipCategoryVar,
                          overallScore);
          } // foreach team
        } // result set
      } // prepared statement

      final WinnerType winnerCriteria = description.getWinner();
      final List<String> awardGroups = Queries.getAwardGroups(connection, tournament.getTournamentID());
      final List<String> judgingStations = Queries.getJudgingStations(connection, tournament.getTournamentID());
      for (final SubjectiveScoreCategory category : description.getSubjectiveCategories()) {
        final String categoryName = category.getName();
        final String categoryVar = getCategoryVarName(categoryName);

        for (final String awardGroup : awardGroups) {
          for (final String judgingStation : judgingStations) {
            FinalComputedScores.iterateOverSubjectiveScores(connection, category, winnerCriteria, tournament,
                                                            awardGroup, judgingStation, (teamNumber,
                                                                                         score,
                                                                                         rank) -> {

                                                              final String teamVar = getTeamVarName(teamNumber);

                                                              output.format("$.finalist.setCategoryScore(%s, %s, %.02f);%n",
                                                                            teamVar, categoryVar, score);

                                                            });
          } // judging station
        } // award group
      } // category

    } // connection

  }

  /**
   * Output the schedules.
   *
   * @param writer where to write
   * @param application the application context
   * @throws SQLException if there is a database error
   */
  public static void outputSchedules(final Writer writer,
                                     final ServletContext application)
      throws SQLException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);

    try (Connection connection = datasource.getConnection()) {

      final int tournament = Queries.getCurrentTournament(connection);
      final Formatter output = new Formatter(writer);

      for (final String division : FinalistSchedule.getAllDivisions(connection, tournament)) {
        output.format("{%n");

        output.format("var division = '%s';%n", division);

        final FinalistSchedule schedule = new FinalistSchedule(connection, tournament, division);

        for (final Map.Entry<String, Boolean> entry : schedule.getCategories().entrySet()) {
          final String categoryTitle = entry.getKey();
          final String quotedCatTitle = WebUtils.quoteJavascriptString(categoryTitle);

          output.format("{%n"); // scope so that variable names are easy
          output.format("  var category = $.finalist.getCategoryByName(%s);%n", quotedCatTitle);
          output.format("  if (null == category) {%n");
          // will be non-numeric because all numeric categories were added earlier
          output.format("    category = $.finalist.addCategory(%s, false);%n", quotedCatTitle);
          output.format("  }%n");
          output.format("  $.finalist.setCategoryPublic(category, %b);%n", entry.getValue());
          output.format("}%n");
        }

        for (final Map.Entry<String, String> entry : schedule.getRooms().entrySet()) {
          final String categoryTitle = entry.getKey();
          final String quotedCatTitle = WebUtils.quoteJavascriptString(categoryTitle);

          output.format("{%n"); // scope so that variable names are easy
          output.format("  var category = $.finalist.getCategoryByName(%s);%n", quotedCatTitle);
          output.format("  if (null == category) {%n");
          // will be non-numeric because all numeric categories were added earlier
          output.format("    category = $.finalist.addCategory(%s, false);%n", quotedCatTitle);
          output.format("  }%n");
          output.format("  $.finalist.setRoom(category, division, %s);%n",
                        WebUtils.quoteJavascriptString(entry.getValue()));
          output.format("}%n");
        }

        /*
         * TODO ticket 447
         * output.format("var schedule = [];%n");
         *
         * for (final FinalistDBRow row : schedule.getSchedule()) {
         * var time = new Time(hours, minutes);
         * var newSlot = new Timeslot(time, slotDuration);
         * schedule.push(newSlot);
         * }
         *
         * output.format("$.finalist.sortSchedule(schedule);%n");
         * output.format("$.finalist.setSchedule(division, schedule);%n");
         */

        output.format("}%n");

      }
    }

  }

}
