/*
 * Copyright (c) 2010 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

import junit.framework.Assert;

import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.JavaScriptPage;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

import fll.TestUtils;
import fll.web.developer.QueryHandler;

/**
 * Utilities for web tests.
 */
public final class WebTestUtils {

  private WebTestUtils() {
    // no instances
  }

  /**
   * Load a page and return the response. If there is an HttpException, call
   * {@link Assert#fail(String)} with a reasonable message.
   */
  public static WebResponse loadPageOld(final WebConversation conversation,
                                        final WebRequest request) throws IOException, SAXException {
    final boolean exceptionOnError = conversation.getExceptionsThrownOnErrorStatus();
    conversation.setExceptionsThrownOnErrorStatus(false);
    try {
      final WebResponse response = conversation.getResponse(request);

      // check response code here and fail with useful message
      checkForServerErrorOld(response);

      return response;
    } finally {
      // restore value
      conversation.setExceptionsThrownOnErrorStatus(exceptionOnError);
    }
  }

  public static Page loadPage(final WebClient conversation,
                              final com.gargoylesoftware.htmlunit.WebRequest request) throws IOException, SAXException {
    final boolean exceptionOnError = conversation.getOptions().isThrowExceptionOnFailingStatusCode();
    conversation.getOptions().setThrowExceptionOnFailingStatusCode(false);
    try {
      final Page response = conversation.getPage(request);

      // check response code here and fail with useful message
      checkForServerError(response);

      return response;
    } finally {
      // restore value
      conversation.getOptions().setThrowExceptionOnFailingStatusCode(exceptionOnError);
    }
  }

  private static void checkForServerErrorOld(final WebResponse response) throws IOException {
    final int code = response.getResponseCode();
    final boolean error;
    if (response.getResponseCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
      error = true;
    } else if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      error = true;
    } else if (response.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
      error = true;
    } else {
      error = false;
    }
    if (error) {
      final String responseMessage = response.getResponseMessage();
      final String text = response.getText();
      final File output = File.createTempFile("server-error", ".html", new File("screenshots"));
      final FileWriter writer = new FileWriter(output);
      writer.write(text);
      writer.close();
      Assert.fail("Error loading page: "
          + response.getURL() + " code: " + code + " message: " + responseMessage
          + " Contents of error page written to: " + output.getAbsolutePath());
    }

  }

  private static void checkForServerError(final Page page) throws IOException {
    final com.gargoylesoftware.htmlunit.WebResponse response = page.getWebResponse();
    final int code = response.getStatusCode();
    final boolean error;
    if (code >= 400) {
      error = true;
    } else {
      error = false;
    }
    if (error) {
      final String responseMessage = response.getStatusMessage();
      final String text = getPageSource(page);
      final File output = File.createTempFile("server-error", ".html", new File("screenshots"));
      final FileWriter writer = new FileWriter(output);
      writer.write(text);
      writer.close();
      Assert.fail("Error loading page: "
          + page.getUrl() + " code: " + code + " message: " + responseMessage + " Contents of error page written to: "
          + output.getAbsolutePath());
    }

  }

  /**
   * Get source of any page type.
   */
  public static String getPageSource(final Page page) {
    if (page instanceof HtmlPage) {
      return ((HtmlPage) page).asXml();
    } else if (page instanceof JavaScriptPage) {
      return ((JavaScriptPage) page).getContent();
    } else if (page instanceof TextPage) {
      return ((TextPage) page).getContent();
    } else {
      // page instanceof UnexpectedPage
      return ((UnexpectedPage) page).getWebResponse().getContentAsString();
    }
  }

  /**
   * Create a new web conversation that is logged in.
   * 
   * @return a web conversation to use
   * @throws SAXException
   * @throws IOException
   */
  public static WebConversation getConversationOld() throws IOException, SAXException {
    final WebConversation conversation = new WebConversation();

    // always login first
    WebRequest request = new GetMethodWebRequest(TestUtils.URL_ROOT
        + "login.jsp");
    WebResponse response = conversation.getResponse(request);
    Assert.assertTrue("Received non-HTML response from web server", response.isHTML());

    WebForm form = response.getFormWithName("login");
    Assert.assertNotNull("Cannot find login form", form);
    request = form.getRequest();
    request.setParameter("user", IntegrationTestUtils.TEST_USERNAME);
    request.setParameter("pass", IntegrationTestUtils.TEST_PASSWORD);
    response = conversation.getResponse(request);
    Assert.assertTrue("Received non-HTML response from web server", response.isHTML());

    final URL responseURL = response.getURL();
    final String address = responseURL.getPath();
    final boolean correctAddress;
    if (address.contains("login.jsp")) {
      correctAddress = false;
    } else {
      correctAddress = true;
    }
    Assert.assertTrue("Unexpected URL after login: "
        + address, correctAddress);

    return conversation;
  }

  public static WebClient getConversation() throws IOException, SAXException {
    final WebClient conversation = new WebClient();

    // always login first
    HtmlPage response = conversation.getPage(TestUtils.URL_ROOT
        + "login.jsp");
    // Assert.assertTrue("Received non-HTML response from web server",
    // response.isHTML());

    HtmlForm form = response.getFormByName("login");
    Assert.assertNotNull("Cannot find login form", form);

    final HtmlTextInput userTextField = form.getInputByName("user");
    userTextField.setValueAttribute(IntegrationTestUtils.TEST_USERNAME);

    final HtmlPasswordInput passTextField = form.getInputByName("pass");
    passTextField.setValueAttribute(IntegrationTestUtils.TEST_PASSWORD);

    final HtmlSubmitInput button = form.getInputByName("submit_login");
    response = button.click();

    final URL responseURL = response.getUrl();
    final String address = responseURL.getPath();
    final boolean correctAddress;
    if (address.contains("login.jsp")) {
      correctAddress = false;
    } else {
      correctAddress = true;
    }
    Assert.assertTrue("Unexpected URL after login: "
        + address, correctAddress);

    return conversation;
  }

  /**
   * Submit a query to developer/QueryHandler, parse the JSON and return it.
   */
  public static QueryHandler.ResultData executeServerQuery(final String query) throws IOException, SAXException {
    final WebClient conversation = getConversation();

    final URL url = new URL(TestUtils.URL_ROOT
        + "developer/QueryHandler");
    final com.gargoylesoftware.htmlunit.WebRequest request = new com.gargoylesoftware.htmlunit.WebRequest(url);
    request.setRequestParameters(Collections.singletonList(new NameValuePair(QueryHandler.QUERY_PARAMETER, query)));

    final Page response = loadPage(conversation, request);
    final String contentType = response.getWebResponse().getContentType();
    if (!"application/json".equals(contentType)) {
      final String text = getPageSource(response);
      final File output = File.createTempFile("json-error", ".html", new File("screenshots"));
      final FileWriter writer = new FileWriter(output);
      writer.write(text);
      writer.close();
      Assert.fail("Error JSON from QueryHandler: "
          + response.getUrl() + " Contents of error page written to: " + output.getAbsolutePath());
    }

    final String responseData = getPageSource(response);

    final ObjectMapper jsonMapper = new ObjectMapper();
    QueryHandler.ResultData result = jsonMapper.readValue(responseData, QueryHandler.ResultData.class);
    Assert.assertNull("SQL Error: "
        + result.getError(), result.getError());

    return result;
  }

}
