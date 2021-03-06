/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fll.Team;
import fll.Tournament;
import fll.TournamentTeam;
import fll.Utilities;
import fll.db.CategoryColumnMapping;
import fll.db.Queries;
import fll.scheduler.TournamentSchedule;
import fll.util.FLLInternalException;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.xml.AbstractGoal;
import fll.xml.ChallengeDescription;
import fll.xml.SubjectiveScoreCategory;
import net.mtu.eggplant.util.sql.SQLFunctions;
import net.mtu.eggplant.xml.XMLUtils;

/**
 * Download the data file for the subjective score app.
 */
@WebServlet("/admin/subjective-data.fll")
public class DownloadSubjectiveData extends BaseFLLServlet {

  public static final String SUBJECTIVE_CATEGORY_NODE_NAME = "subjectiveCategory";

  public static final String SCORE_NODE_NAME = "score";

  public static final String SCORES_NODE_NAME = "scores";

  public static final String SUBSCORE_NODE_NAME = "subscore";

  /**
   * Name of schedule in the zip file.
   */
  public static final String SCHEDULE_ENTRY_NAME = "schedule.ser";

  /**
   * Name of entry for the schedule column mappings in the zip file.
   */
  public static final String MAPPING_ENTRY_NAME = "mappings.ser";

  /**
   * Name of challenge description in the zip file.
   */
  public static final String CHALLENGE_ENTRY_NAME = "challenge.xml";

  /**
   * Name of the score entry in the zip file.
   */
  public static final String SCORE_ENTRY_NAME = "score.xml";

  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session)
      throws IOException, ServletException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    Connection connection = null;
    try {
      connection = datasource.getConnection();
      final Document challengeDocument = ApplicationAttributes.getChallengeDocument(application);
      final ChallengeDescription challengeDescription = ApplicationAttributes.getChallengeDescription(application);

      final int currentTournament = Queries.getCurrentTournament(connection);
      final TournamentSchedule schedule;
      if (TournamentSchedule.scheduleExistsInDatabase(connection, currentTournament)) {
        schedule = new TournamentSchedule(connection, currentTournament);
      } else {
        schedule = null;
      }

      final Collection<CategoryColumnMapping> scheduleColumnMappings = CategoryColumnMapping.load(connection,
                                                                                                  currentTournament);

      final Tournament tournament = Tournament.findTournamentByID(connection, currentTournament);

      final DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
      final String dateStr = df.format(new Date());

      final String filename = String.format("%s_%s_subjective-data.fll", tournament.getName(), dateStr);

      response.reset();
      response.setContentType("application/zip");
      response.setHeader("Content-Disposition", "attachment; filename=\""
          + filename
          + "\"");
      writeSubjectiveData(connection, challengeDocument, challengeDescription, schedule, scheduleColumnMappings,
                          response.getOutputStream());
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    } finally {
      SQLFunctions.close(connection);
    }
  }

  /**
   * Create a document to hold subject scores for the tournament described in
   * challengeDocument.
   * 
   * @param teams the teams for this tournament
   * @param connection the database connection used to retrieve the judge
   *          information
   * @param currentTournament the tournament to generate the document for, used
   *          for deciding which set of judges to use
   * @return the document
   */
  @SuppressFBWarnings(value = { "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING" }, justification = "Category determines table name")
  public static Document createSubjectiveScoresDocument(final ChallengeDescription challengeDescription,
                                                        final Collection<? extends Team> teams,
                                                        final Connection connection,
                                                        final int currentTournament)
      throws SQLException {
    ResultSet rs = null;
    ResultSet rs2 = null;
    PreparedStatement prep = null;
    PreparedStatement prep2 = null;
    try {
      final Tournament tournament = Tournament.findTournamentByID(connection, currentTournament);

      prep = connection.prepareStatement("SELECT id, station FROM Judges WHERE category = ? AND Tournament = ?");
      prep.setInt(2, currentTournament);

      final Document document = XMLUtils.DOCUMENT_BUILDER.newDocument();
      final Element top = document.createElementNS(null, SCORES_NODE_NAME);
      document.appendChild(top);

      top.setAttribute("tournamentName", tournament.getName());
      if (null != tournament.getDescription()) {
        top.setAttribute("tournamentDescription", tournament.getDescription());
      }

      for (final SubjectiveScoreCategory categoryDescription : challengeDescription.getSubjectiveCategories()) {
        final String categoryName = categoryDescription.getName();
        final Element categoryElement = document.createElementNS(null, SUBJECTIVE_CATEGORY_NODE_NAME);
        top.appendChild(categoryElement);
        categoryElement.setAttributeNS(null, "name", categoryName);

        prep.setString(1, categoryName);
        rs = prep.executeQuery();
        while (rs.next()) {
          final String judge = rs.getString(1);
          final String judgingStation = rs.getString(2);

          for (final Team team : teams) {
            final String teamJudgingGroup = Queries.getJudgingGroup(connection, team.getTeamNumber(),
                                                                    currentTournament);
            if (judgingStation.equals(teamJudgingGroup)) {
              final String teamDiv = Queries.getEventDivision(connection, team.getTeamNumber());

              final Element scoreElement = document.createElementNS(null, SCORE_NODE_NAME);
              categoryElement.appendChild(scoreElement);

              scoreElement.setAttributeNS(null, "teamName", team.getTeamName());
              scoreElement.setAttributeNS(null, "teamNumber", String.valueOf(team.getTeamNumber()));
              scoreElement.setAttributeNS(null, "division", teamDiv);
              scoreElement.setAttributeNS(null, "judging_station", teamJudgingGroup);
              scoreElement.setAttributeNS(null, "organization", team.getOrganization());
              scoreElement.setAttributeNS(null, "judge", judge);

              prep2 = connection.prepareStatement("SELECT * FROM "
                  + categoryName
                  + " WHERE TeamNumber = ? AND Tournament = ? AND Judge = ?");
              prep2.setInt(1, team.getTeamNumber());
              prep2.setInt(2, currentTournament);
              prep2.setString(3, judge);
              rs2 = prep2.executeQuery();
              if (rs2.next()) {
                for (final AbstractGoal goalDescription : categoryDescription.getGoals()) {
                  final String goalName = goalDescription.getName();
                  final String value = rs2.getString(goalName);
                  if (!rs2.wasNull()) {
                    final Element subscoreElement = document.createElementNS(null, SUBSCORE_NODE_NAME);
                    scoreElement.appendChild(subscoreElement);

                    subscoreElement.setAttributeNS(null, "name", goalName);
                    subscoreElement.setAttributeNS(null, "value", value);
                  }
                }
                scoreElement.setAttributeNS(null, "NoShow", rs2.getString("NoShow").toLowerCase());
              } else {
                scoreElement.setAttributeNS(null, "NoShow", "false");
              }
            }
          }
        }
      }
      return document;
    } finally {
      SQLFunctions.close(rs);
      SQLFunctions.close(rs2);
      SQLFunctions.close(prep);
      SQLFunctions.close(prep2);
    }
  }

  /**
   * Write out the subjective scores data for the current tournament.
   * 
   * @param stream where to write the scores file
   * @throws IOException
   */
  public static void writeSubjectiveData(final Connection connection,
                                         final Document challengeDocument,
                                         final ChallengeDescription challengeDescription,
                                         final TournamentSchedule schedule,
                                         final Collection<CategoryColumnMapping> scheduleColumnMappings,
                                         final OutputStream stream)
      throws IOException, SQLException {
    final Map<Integer, TournamentTeam> tournamentTeams = Queries.getTournamentTeams(connection);
    final int tournament = Queries.getCurrentTournament(connection);

    final ZipOutputStream zipOut = new ZipOutputStream(stream);

    // write the raw files before creating the writer wrapper
    if (null != schedule) {
      zipOut.putNextEntry(new ZipEntry(SCHEDULE_ENTRY_NAME));
      final ObjectOutputStream scheduleOut = new ObjectOutputStream(zipOut);
      scheduleOut.writeObject(schedule);
      zipOut.closeEntry();
    }

    if (null != scheduleColumnMappings) {
      zipOut.putNextEntry(new ZipEntry(MAPPING_ENTRY_NAME));
      final ObjectOutputStream scheduleOut = new ObjectOutputStream(zipOut);
      // convert to LinkedList to be sure that it's serializable
      scheduleOut.writeObject(new LinkedList<CategoryColumnMapping>(scheduleColumnMappings));
      zipOut.closeEntry();
    }

    // write text files with writer
    final Writer writer = new OutputStreamWriter(zipOut, Utilities.DEFAULT_CHARSET);

    zipOut.putNextEntry(new ZipEntry(CHALLENGE_ENTRY_NAME));
    XMLUtils.writeXML(challengeDocument, writer, Utilities.DEFAULT_CHARSET.name());
    zipOut.closeEntry();

    final Document scoreDocument = createSubjectiveScoresDocument(challengeDescription, tournamentTeams.values(),
                                                                  connection, tournament);

    try {
      validateXML(scoreDocument);
    } catch (final SAXException e) {
      throw new FLLInternalException("Subjective XML document is invalid", e);
    }

    zipOut.putNextEntry(new ZipEntry(SCORE_ENTRY_NAME));
    XMLUtils.writeXML(scoreDocument, writer, Utilities.DEFAULT_CHARSET.name());
    zipOut.closeEntry();

    zipOut.close();
  }

  /**
   * Validate the subjective XML document.
   * 
   * @throws SAXException on an error
   */
  public static void validateXML(final org.w3c.dom.Document document) throws SAXException {
    try {
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      final Source schemaFile = new StreamSource(classLoader.getResourceAsStream("fll/resources/subjective.xsd"));
      final Schema schema = factory.newSchema(schemaFile);

      final Validator validator = schema.newValidator();

      try {
        validator.validate(new DOMSource(document));
      } catch (final SAXException se) {
        // For some reason the SubjectiveFrameTest fails the above validation
        // and
        // writing the document out and reading it back in causes everything to
        // work.
        // JPS 2013-07-03
        final java.io.File temp = java.io.File.createTempFile("fll", "xml");
        XMLUtils.writeXML(document,
                                  new java.io.OutputStreamWriter(new java.io.FileOutputStream(temp),
                                                                 Utilities.DEFAULT_CHARSET),
                                  Utilities.DEFAULT_CHARSET.name());
        final InputStream scoreStream = new java.io.FileInputStream(temp);
        final Document tempDocument = XMLUtils.parseXMLDocument(scoreStream);
        if (!temp.delete()) {
          temp.deleteOnExit();
        }
        validator.validate(new DOMSource(tempDocument));
      }

    } catch (final IOException e) {
      throw new RuntimeException("Internal error, should never get IOException here", e);
    }
  }

}
