/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.developer.importdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;



import fll.Tournament;

import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.SessionAttributes;

/**
 * Servlet to create a tournament.
 * 
 * @author jpschewe
 */
@WebServlet("/developer/importdb/CreateTournament")
public class CreateTournament extends BaseFLLServlet {

  private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger();

  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session)
      throws IOException, ServletException {
    final StringBuilder message = new StringBuilder();

    // unset redirect url
    session.setAttribute(SessionAttributes.REDIRECT_URL, null);

    try {
      final String answer = (String) request.getParameter("submit");
      if (LOG.isTraceEnabled()) {
        LOG.trace("Submit to CreateTournament: '"
            + answer
            + "'");
      }

      if ("Yes".equals(answer)) {
        createSelectedTournament(message, application, session);
      } else {
        message.append("<p>Canceled request to create tournament</p>");
        session.setAttribute(SessionAttributes.REDIRECT_URL, "selectTournament.jsp");
      }
    } catch (final SQLException sqle) {
      LOG.error(sqle, sqle);
      throw new RuntimeException("Error talking to the database", sqle);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("Message is: "
          + message);
      LOG.trace("Redirect URL is: "
          + SessionAttributes.getRedirectURL(session));
    }

    session.setAttribute("message", message.toString());
    response.sendRedirect(response.encodeRedirectURL(SessionAttributes.getRedirectURL(session)));
  }

  /**
   * Create the tournament that is in the session variable
   * <code>selectedTournament</code>.
   * 
   * @param session the session
   * @throws SQLException
   */
  private static void createSelectedTournament(final StringBuilder message,
                                               final ServletContext application,
                                               final HttpSession session)
      throws SQLException {
    final ImportDbSessionInfo sessionInfo = SessionAttributes.getNonNullAttribute(session,
                                                                                  ImportDBDump.IMPORT_DB_SESSION_KEY,
                                                                                  ImportDbSessionInfo.class);
    final String tournament = sessionInfo.getTournamentName();
    Objects.requireNonNull(tournament, "Missing tournament name to import");

    final DataSource sourceDataSource = sessionInfo.getImportDataSource();
    Objects.requireNonNull(sourceDataSource, "Missing the import data source");

    final DataSource destDataSource = ApplicationAttributes.getDataSource(application);

    try (Connection sourceConnection = sourceDataSource.getConnection();
        Connection destConnection = destDataSource.getConnection()) {
      createTournament(sourceConnection, destConnection, tournament, message, session);
    }
  }

  /**
   * Create a tournament from the source in the dest. This recurses on
   * nextTournament if needed.
   * 
   * @throws SQLException
   */
  private static void createTournament(final Connection sourceConnection,
                                       final Connection destConnection,
                                       final String tournamentName,
                                       final StringBuilder message,
                                       final HttpSession session)
      throws SQLException {
    final Tournament sourceTournament = Tournament.findTournamentByName(sourceConnection, tournamentName);
    Tournament.createTournament(destConnection, sourceTournament.getName(), sourceTournament.getDescription(),
                                sourceTournament.getDate());
    message.append("<p>Created tournament "
        + sourceTournament.getName()
        + "</p>");

    if (null == SessionAttributes.getRedirectURL(session)) {
      session.setAttribute(SessionAttributes.REDIRECT_URL, "CheckTournamentExists");
    }

  }

}
