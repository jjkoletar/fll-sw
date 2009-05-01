<%@ include file="/WEB-INF/jspf/init.jspf" %>

<%@ page import="fll.ScoreStandardization" %>
<%@ page import="fll.db.Queries" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="org.w3c.dom.Document" %>
<%@ page import="fll.web.ApplicationAttributes"%>
<%@ page import="fll.web.SessionAttributes" %>
<%@ page import="javax.sql.DataSource" %>

<%
final Document challengeDocument = ApplicationAttributes.getChallengeDocument(application);
final DataSource datasource = SessionAttributes.getDataSource(session);
final Connection connection = datasource.getConnection();
final String currentTournament = Queries.getCurrentTournament(connection);
  
ScoreStandardization.updateTeamTotalScores(connection, challengeDocument, currentTournament);
final String errorMsg = ScoreStandardization.checkDataConsistency(connection);

%>

<html>
  <head>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/style.jsp'/>" />
    <title><x:out select="$challengeDocument/fll/@title"/> (Summarize Scores)</title>
  </head>

  <body>
    <h1><x:out select="$challengeDocument/fll/@title"/> (Summarize Scores)</h1>

<%if(null == errorMsg) {%>
  <a href="index.jsp">Normally you'd be redirected here</a>
  <% response.sendRedirect(response.encodeRedirectURL("index.jsp?message=Successfully+summarized+scores")); %>
<%} else {%>
<p><font color='red'><%=errorMsg%></font></p>
<%}%>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>
  </body>
</html>
