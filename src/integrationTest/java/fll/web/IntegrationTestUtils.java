/*
 * Copyright (c) 2010 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.LifecycleException;
import org.apache.http.Header;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.fest.swing.image.ScreenshotTaker;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.Select;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.ObjectMapper;

import fll.TestUtils;
import fll.TomcatLauncher;
import fll.Tournament;
import fll.Utilities;
import fll.util.FLLInternalException;
import fll.web.api.TournamentsServlet;
import fll.xml.BracketSortType;
import io.github.bonigarcia.wdm.DriverManagerType;
import io.github.bonigarcia.wdm.WebDriverManager;
import net.mtu.eggplant.xml.XMLUtils;

/**
 * Some utilities for integration tests.
 */
public final class IntegrationTestUtils {

  private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

  public static final String TEST_USERNAME = "fll";

  public static final String TEST_PASSWORD = "Lego";

  /**
   * How long to wait for pages to load before checking for elements.
   */
  public static final long WAIT_FOR_PAGE_LOAD_MS = 2500;

  private IntegrationTestUtils() {
    // no instances
  }

  /**
   * Check if an element exists.
   */
  public static boolean isElementPresent(final WebDriver selenium,
                                         final By search) {
    boolean elementFound = false;
    try {
      selenium.findElement(search);
      elementFound = true;
    } catch (final NoSuchElementException e) {
      elementFound = false;
    }
    return elementFound;
  }

  /**
   * Load a page and check to make sure the page didn't crash.
   *
   * @param selenium the test controller
   * @param url the page to load
   * @throws IOException if there is an error from selenium
   * @throws InterruptedException if we are interrupted waiting for the page to
   *           load
   */
  public static void loadPage(final WebDriver selenium,
                              final String url)
      throws IOException, InterruptedException {
    selenium.get(url);

    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    assertNoException(selenium);
  }

  /**
   * Assert that the current page is not the error handler page.
   */
  public static void assertNoException(final WebDriver selenium) {
    assertFalse(isElementPresent(selenium, By.id("exception-handler")), "Error loading page");
  }

  /**
   * Initialize the database using the given challenge document.
   *
   * @param driver the test controller
   * @param challengeDocument the challenge descriptor
   * @throws IOException
   * @throws InterruptedException
   */
  public static void initializeDatabase(final WebDriver driver,
                                        final Document challengeDocument)
      throws IOException, InterruptedException {
    assertNotNull(challengeDocument);

    final Path challengeFile = Files.createTempFile("fll", ".xml");
    try (Writer writer = Files.newBufferedWriter(challengeFile, Utilities.DEFAULT_CHARSET)) {
      XMLUtils.writeXML(challengeDocument, writer);
    }
    try {
      initializeDatabase(driver, challengeFile);
    } finally {
      Files.delete(challengeFile);
    }
  }

  /**
   * Initialize the database using the given challenge descriptor.
   *
   * @param driver the test controller
   * @param challengeStream the challenge descriptor
   * @throws IOException
   * @throws InterruptedException
   */
  public static void initializeDatabase(final WebDriver driver,
                                        final InputStream challengeStream)
      throws IOException, InterruptedException {
    assertNotNull(challengeStream);

    final Path challengeFile = Files.createTempFile("fll", ".xml");
    Files.copy(challengeStream, challengeFile, StandardCopyOption.REPLACE_EXISTING);
    try {
      initializeDatabase(driver, challengeFile);
    } finally {
      Files.delete(challengeFile);
    }
  }

  /**
   * Initialize the database using the given challenge descriptor.
   *
   * @param driver the test controller
   * @param challengeFile a file to read the challenge description from. This
   *          file will not be deleted.
   * @throws InterruptedException
   * @throws IOException
   */
  public static void initializeDatabase(final WebDriver driver,
                                        final Path challengeFile)
      throws InterruptedException {

    driver.get(TestUtils.URL_ROOT
        + "setup/");
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    if (isElementPresent(driver, By.name("submit_login"))) {
      login(driver);

      driver.get(TestUtils.URL_ROOT
          + "setup/");
      Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);
    }

    final WebElement fileEle = driver.findElement(By.name("xmldocument"));
    fileEle.sendKeys(challengeFile.toAbsolutePath().toString());

    final WebElement reinitDB = driver.findElement(By.name("reinitializeDatabase"));
    reinitDB.click();

    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    try {
      final Alert confirmCreateDB = driver.switchTo().alert();
      LOGGER.info("Confirmation text: "
          + confirmCreateDB.getText());
      confirmCreateDB.accept();
    } catch (final NoAlertPresentException e) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("No alert found, assuming the database was empty and didn't need an alert.");
      }
    }

    Thread.sleep(2
        * WAIT_FOR_PAGE_LOAD_MS);

    driver.findElement(By.id("success"));

    // setup user
    final WebElement userElement = driver.findElement(By.name("user"));
    userElement.sendKeys(TEST_USERNAME);

    final WebElement passElement = driver.findElement(By.name("pass"));
    passElement.sendKeys(TEST_PASSWORD);

    final WebElement passCheckElement = driver.findElement(By.name("pass_check"));
    passCheckElement.sendKeys(TEST_PASSWORD);

    final WebElement submitElement = driver.findElement(By.name("submit_create_user"));
    submitElement.click();
    Thread.sleep(2
        * WAIT_FOR_PAGE_LOAD_MS);

    driver.findElement(By.id("success-create-user"));

    login(driver);

  }

  /**
   * Initialize a database from a zip file.
   *
   * @param selenium the test controller
   * @param inputStream input stream that has database to load in it, this input
   *          stream is closed by this method upon successful completion
   * @throws IOException
   * @throws InterruptedException
   */
  public static void initializeDatabaseFromDump(final WebDriver selenium,
                                                final InputStream inputStream)
      throws IOException, InterruptedException {
    assertNotNull(inputStream);
    final File dumpFile = IntegrationTestUtils.storeInputStreamToFile(inputStream);
    try {
      selenium.get(TestUtils.URL_ROOT
          + "setup/");
      Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

      if (isElementPresent(selenium, By.name("submit_login"))) {
        login(selenium);

        selenium.get(TestUtils.URL_ROOT
            + "setup/");
        Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);
      }

      final WebElement dbEle = selenium.findElement(By.name("dbdump"));
      dbEle.sendKeys(dumpFile.getAbsolutePath());

      final WebElement createEle = selenium.findElement(By.name("createdb"));
      createEle.click();

      Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

      try {
        final Alert confirmCreateDB = selenium.switchTo().alert();
        LOGGER.info("Confirmation text: "
            + confirmCreateDB.getText());
        confirmCreateDB.accept();
      } catch (final NoAlertPresentException e) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("No alert found, assuming the database was empty and didn't need an alert.");
        }
      }

      Thread.sleep(2
          * WAIT_FOR_PAGE_LOAD_MS);

      selenium.findElement(By.id("success"));

      // setup user
      final WebElement userElement = selenium.findElement(By.name("user"));
      userElement.sendKeys(TEST_USERNAME);

      final WebElement passElement = selenium.findElement(By.name("pass"));
      passElement.sendKeys(TEST_PASSWORD);

      final WebElement passCheckElement = selenium.findElement(By.name("pass_check"));
      passCheckElement.sendKeys(TEST_PASSWORD);

      final WebElement submitElement = selenium.findElement(By.name("submit_create_user"));
      submitElement.click();
      Thread.sleep(2
          * WAIT_FOR_PAGE_LOAD_MS);

      selenium.findElement(By.id("success-create-user"));

      login(selenium);
    } finally {
      if (!dumpFile.delete()) {
        dumpFile.deleteOnExit();
      }
    }
    login(selenium);
  }

  /**
   * Defaults filePrefix to "fll".
   *
   * @see #storeScreenshot(String, WebDriver)
   */
  public static void storeScreenshot(final WebDriver driver) throws IOException {
    storeScreenshot("fll", driver);
  }

  /**
   * Store screenshot and other information for debugging the error.
   *
   * @param filePrefix prefix for the files that are created
   * @param driver
   * @throws IOException
   */
  public static void storeScreenshot(final String filePrefix,
                                     final WebDriver driver)
      throws IOException {
    final Path screenshotsDir = Paths.get("screenshots");
    if (!Files.exists(screenshotsDir)) {
      Files.createDirectories(screenshotsDir);
    }

    final Path tempDir = Files.createTempDirectory(screenshotsDir, filePrefix);

    if (driver instanceof TakesScreenshot) {
      final Path screenshot = tempDir.resolve("screenshot.png");

      final File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      Files.copy(scrFile.toPath(), screenshot);
      LOGGER.info("Screenshot saved to "
          + screenshot.toAbsolutePath().toString());
    } else {
      LOGGER.warn("Unable to get screenshot");
    }

    final Path htmlFile = tempDir.resolve("page.html");

    final String html = driver.getPageSource();
    try (BufferedWriter writer = Files.newBufferedWriter(htmlFile)) {
      writer.write(html);
    }
    LOGGER.info("HTML saved to "
        + htmlFile.toAbsolutePath().toString());

    // get the database
    final Path dbOutput = tempDir.resolve("database.flldb");
    LOGGER.info("Downloading database to "
        + dbOutput.toAbsolutePath());
    downloadFile(new URL(TestUtils.URL_ROOT
        + "admin/database.flldb"), null, dbOutput);

  }

  /**
   * Copy the contents of a stream to a temporary file.
   *
   * @param inputStream the data to store in the temporary file
   * @return the temporary file, you need to delete it
   * @throws IOException
   */
  public static File storeInputStreamToFile(final InputStream inputStream) throws IOException {
    final File tempFile = File.createTempFile("fll", null);
    try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
      final byte[] buffer = new byte[1042];
      int bytesRead;
      while (-1 != (bytesRead = inputStream.read(buffer))) {
        outputStream.write(buffer, 0, bytesRead);
      }
    }

    return tempFile;
  }

  /**
   * Login to fll
   *
   * @throws InterruptedException
   */
  public static void login(final WebDriver driver) throws InterruptedException {
    driver.get(TestUtils.URL_ROOT
        + "login.jsp");

    final WebElement userElement = driver.findElement(By.name("user"));
    userElement.sendKeys(TEST_USERNAME);

    final WebElement passElement = driver.findElement(By.name("pass"));
    passElement.sendKeys(TEST_PASSWORD);

    final WebElement submitElement = driver.findElement(By.name("submit_login"));
    submitElement.click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

  }

  private static String readAll(final Reader rd) throws IOException {
    final StringBuilder sb = new StringBuilder();
    int cp;
    while ((cp = rd.read()) != -1) {
      sb.append((char) cp);
    }
    return sb.toString();
  }

  private static String readJSON(final String url) throws MalformedURLException, IOException {
    final InputStream is = new URL(url).openStream();
    try {
      final BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      final String jsonText = readAll(rd);
      return jsonText;
    } finally {
      is.close();
    }
  }

  /**
   * Find a tournament by name using the JSON API.
   *
   * @param tournamentName name of tournament
   * @return the tournament or null if not found
   */
  public static Tournament getTournamentByName(final String tournamentName) throws IOException {
    final String json = readJSON(TestUtils.URL_ROOT
        + "api/Tournaments");

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Tournaments json: "
          + json);
    }

    // get the JSON
    final ObjectMapper jsonMapper = new ObjectMapper();
    final Reader reader = new StringReader(json);

    final Collection<Tournament> tournaments = jsonMapper.readValue(reader,
                                                                    TournamentsServlet.TournamentsTypeInformation.INSTANCE);

    for (final Tournament tournament : tournaments) {
      if (tournament.getName().equals(tournamentName)) {
        return tournament;
      }
    }

    return null;
  }

  /**
   * Add a team to a tournament.
   *
   * @throws InterruptedException
   */
  public static void addTeam(final WebDriver selenium,
                             final int teamNumber,
                             final String teamName,
                             final String organization,
                             final String division,
                             final String tournamentName)
      throws IOException, InterruptedException {
    final Tournament tournament = getTournamentByName(tournamentName);

    loadPage(selenium, TestUtils.URL_ROOT
        + "admin/index.jsp");

    selenium.findElement(By.linkText("Add a team")).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    selenium.findElement(By.name("teamNumber")).sendKeys(String.valueOf(teamNumber));
    selenium.findElement(By.name("teamName")).sendKeys(teamName);
    selenium.findElement(By.name("organization")).sendKeys(organization);

    selenium.findElement(By.id("tournament_"
        + tournament.getTournamentID())).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    final WebElement eventDivision = selenium.findElement(By.id("event_division_"
        + tournament.getTournamentID()));
    final Select eventDivisionSel = new Select(eventDivision);
    eventDivisionSel.selectByValue(division);

    final WebElement judgingStation = selenium.findElement(By.id("judging_station_"
        + tournament.getTournamentID()));
    final Select judgingStationSel = new Select(judgingStation);
    judgingStationSel.selectByValue(division);

    selenium.findElement(By.name("commit")).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    selenium.findElement(By.id("success"));
  }

  /**
   * Set the current tournament by name.
   *
   * @param tournamentName the name of the tournament to make the current
   *          tournament
   * @throws IOException
   * @throws InterruptedException
   */
  public static void setTournament(final WebDriver selenium,
                                   final String tournamentName)
      throws IOException, InterruptedException {
    loadPage(selenium, TestUtils.URL_ROOT
        + "admin/index.jsp");

    final WebElement currentTournament = selenium.findElement(By.id("currentTournamentSelect"));

    final Select currentTournamentSel = new Select(currentTournament);
    String tournamentID = null;
    for (final WebElement option : currentTournamentSel.getOptions()) {
      final String text = option.getText();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("setTournament option: "
            + text);
      }
      if (text.endsWith("[ "
          + tournamentName
          + " ]")) {
        tournamentID = option.getAttribute("value");
      }
    }
    assertNotNull(tournamentID, "Could not find tournament with name: "
        + tournamentName);

    currentTournamentSel.selectByValue(tournamentID);

    final WebElement changeTournament = selenium.findElement(By.name("change_tournament"));
    changeTournament.click();

    assertNotNull(selenium.findElement(By.id("success")));
  }

  /**
   * Create firefox web driver used for most integration tests.
   *
   * @see #createWebDriver(WebDriverType)
   */
  public static WebDriver createWebDriver() {
    return createWebDriver(WebDriverType.FIREFOX);
  }

  public enum WebDriverType {
    FIREFOX, CHROME
  }

  private static Set<WebDriverType> mInitializedWebDrivers = new HashSet<>();

  /**
   * Create a web driver and set appropriate timeouts on it.
   */
  public static WebDriver createWebDriver(final WebDriverType type) {
    final WebDriver selenium;
    switch (type) {
    case FIREFOX:
      selenium = createFirefoxWebDriver();
      break;
    case CHROME:
      selenium = createChromeWebDriver();
      break;
    default:
      throw new IllegalArgumentException("Unknown web driver type: "
          + type);
    }

    selenium.manage().timeouts().implicitlyWait(WAIT_FOR_PAGE_LOAD_MS, TimeUnit.MILLISECONDS);
    selenium.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);

    // get some information from the driver
    LOGGER.info("Selenium driver: "
        + selenium.getClass().getName());
    if (selenium instanceof JavascriptExecutor) {
      final JavascriptExecutor jsSelenium = (JavascriptExecutor) selenium;
      final String uAgent = jsSelenium.executeScript("return navigator.userAgent;").toString();
      LOGGER.info("User agent: "
          + uAgent);
    }

    return selenium;
  }

  private static WebDriver createFirefoxWebDriver() {
    if (!mInitializedWebDrivers.contains(WebDriverType.FIREFOX)) {
      WebDriverManager.getInstance(DriverManagerType.FIREFOX).setup();
      mInitializedWebDrivers.add(WebDriverType.FIREFOX);
    }

    // final DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    // capabilities.setCapability("marionette", true);
    // final WebDriver selenium = new FirefoxDriver(capabilities);

    final FirefoxOptions options = new FirefoxOptions();
    // options.setLogLevel(org.openqa.selenium.firefox.FirefoxDriverLogLevel.TRACE);
    final WebDriver selenium = new FirefoxDriver(options);

    // final WebDriver selenium = new FirefoxDriver();
    return selenium;
  }

  private static WebDriver createChromeWebDriver() {
    if (!mInitializedWebDrivers.contains(WebDriverType.CHROME)) {
      WebDriverManager.getInstance(DriverManagerType.CHROME).setup();
      mInitializedWebDrivers.add(WebDriverType.CHROME);
    }

    final WebDriver selenium = new ChromeDriver();

    return selenium;
  }

  public static void initializePlayoffsForAwardGroup(final WebDriver selenium,
                                                     final String awardGroup)
      throws IOException, InterruptedException {
    initializePlayoffsForAwardGroup(selenium, awardGroup, BracketSortType.SEEDING);
  }

  public static void initializePlayoffsForAwardGroup(final WebDriver selenium,
                                                     final String awardGroup,
                                                     final BracketSortType bracketSort)
      throws IOException, InterruptedException {
    loadPage(selenium, TestUtils.URL_ROOT
        + "playoff");

    selenium.findElement(By.id("create-bracket")).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);

    selenium.findElement(By.xpath("//input[@value='Create Head to Head Bracket for Award Group "
        + awardGroup
        + "']")).click();
    assertTrue(isElementPresent(selenium, By.id("success")), "Error creating bracket for award group: "
        + awardGroup);

    final Select initDiv = new Select(selenium.findElement(By.id("initialize-division")));
    initDiv.selectByValue(awardGroup);
    selenium.findElement(By.id("initialize_brackets")).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);
    assertFalse(isElementPresent(selenium, By.id("exception-handler")), "Error loading page");

    final Select sort = new Select(selenium.findElement(By.id("sort")));
    sort.selectByValue(bracketSort.name());
    selenium.findElement(By.id("submit")).click();
    Thread.sleep(WAIT_FOR_PAGE_LOAD_MS);
    assertFalse(isElementPresent(selenium, By.id("exception-handler")), "Error loading page");
  }

  /**
   * Try harder to find elements.
   */
  public static WebElement findElement(final WebDriver selenium,
                                       final By by,
                                       final int maxAttempts) {
    int attempts = 0;
    WebElement e = null;
    while (e == null
        && attempts <= maxAttempts) {
      try {
        e = selenium.findElement(by);
      } catch (final NoSuchElementException ex) {
        ++attempts;
        e = null;
        if (attempts >= maxAttempts) {
          throw ex;
        } else {
          LOGGER.warn("Trouble finding element, trying again", ex);
        }
      }
    }

    return e;
  }

  /**
   * Change the number of seeding rounds for the current tournament.
   *
   * @param selenium the driver
   * @param newValue the new value
   * @throws NoSuchElementException if there was a problem changing the value
   * @throws IOException if there is an error talking to selenium
   * @throws InterruptedException
   */
  public static void changeNumSeedingRounds(final WebDriver selenium,
                                            final int newValue)
      throws NoSuchElementException, IOException, InterruptedException {
    IntegrationTestUtils.loadPage(selenium, TestUtils.URL_ROOT
        + "admin/edit_tournament_parameters.jsp");

    selenium.findElement(By.id("seeding_rounds")).sendKeys(String.valueOf(newValue));

    selenium.findElement(By.id("submit")).click();

    selenium.findElement(By.id("success"));
  }

  /**
   * Get the id of the current tournament
   *
   * @throws IOException
   * @throws InterruptedException
   */
  public static int getCurrentTournamentId(final WebDriver selenium) throws IOException, InterruptedException {
    loadPage(selenium, TestUtils.URL_ROOT
        + "admin/index.jsp");

    final WebElement currentTournament = selenium.findElement(By.id("currentTournamentSelect"));

    final Select currentTournamentSel = new Select(currentTournament);
    for (final WebElement option : currentTournamentSel.getOptions()) {
      if (option.isSelected()) {
        final String idStr = option.getAttribute("value");
        return Integer.parseInt(idStr);
      }
    }
    throw new FLLInternalException("Cannot find default tournament");
  }

  /**
   * Download the specified file and check the content type.
   * If the content type doesn't match an assertion violation will be thrown.
   *
   * @param urlToLoad the page to load
   * @param the expected content type.
   *          If the expected type is null, skip this check.
   * @param destination where to save the file. If null don't save the file.
   *          Any existing file will be
   *          overwritten.
   */
  public static void downloadFile(final URL urlToLoad,
                                  final String expectedContentType,
                                  final Path destination)
      throws ClientProtocolException, IOException {

    try (final CloseableHttpClient client = HttpClientBuilder.create().build()) {
      final BasicHttpContext localContext = new BasicHttpContext();

      // if (this.mimicWebDriverCookieState) {
      // localContext.setAttribute(ClientContext.COOKIE_STORE,
      // mimicCookieState(selenium.manage().getCookies()));
      // }
      final HttpRequestBase requestMethod = new HttpGet();
      requestMethod.setURI(urlToLoad.toURI());
      // HttpParams httpRequestParameters = requestMethod.getParams();
      // httpRequestParameters.setParameter(ClientPNames.HANDLE_REDIRECTS,
      // this.followRedirects);
      // requestMethod.setParams(httpRequestParameters);

      try (CloseableHttpResponse response = client.execute(requestMethod, localContext)) {

        if (null != expectedContentType) {
          final Header contentTypeHeader = response.getFirstHeader("Content-type");
          assertNotNull(contentTypeHeader, "Null content type header: "
              + urlToLoad.toString());
          final String contentType = contentTypeHeader.getValue().split(";")[0].trim();
          assertEquals(expectedContentType, contentType, "Unexpected content type from: "
              + urlToLoad.toString());
        }

        if (null != destination) {
          try (final InputStream stream = response.getEntity().getContent()) {
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
          } // try create stream
        } // non-null destination
      }
    } catch (final URISyntaxException e) {
      throw new FLLInternalException("Got exception turning URL into URI, this shouldn't happen", e);
    }
  }

  /**
   * Used to allocate tomcat around a test.
   */
  public static class TomcatRequired
      implements BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {
    private static final String TOMCAT_LAUNCHER_KEY = "TomcatLauncher";

    private static final String WEBDRIVER_KEY = "WebDriver";

    private Store getStore(final ExtensionContext context) {
      return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }

    @Override
    public void beforeTestExecution(final ExtensionContext context) throws Exception {
      final TomcatLauncher launcher = new TomcatLauncher();
      try {
        launcher.start();
      } catch (final LifecycleException e) {
        throw new RuntimeException(e);
      }
      getStore(context).put(TOMCAT_LAUNCHER_KEY, launcher);
    }

    @Override
    public void afterTestExecution(final ExtensionContext context) throws Exception {
      final TomcatLauncher launcher = getStore(context).remove(TOMCAT_LAUNCHER_KEY, TomcatLauncher.class);
      try {
        if (null != launcher) {
          launcher.stop();
        }
      } catch (final LifecycleException e) {
        throw new RuntimeException(e);
      }

      final WebDriver selenium = getStore(context).remove(WEBDRIVER_KEY, WebDriver.class);
      if (null != selenium) {
        selenium.quit();
      }
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
                                     final ExtensionContext extensionContext)
        throws ParameterResolutionException {
      final Class<?> type = parameterContext.getParameter().getType();
      return WebDriver.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
                                   final ExtensionContext extensionContext)
        throws ParameterResolutionException {
      final WebDriver selenium = createWebDriver();

      getStore(extensionContext).put(WEBDRIVER_KEY, selenium);

      return selenium;
    }
  }

  public static final ScreenshotTaker SCREENSHOT_TAKER = new ScreenshotTaker();

  /**
   * Save a screen shot. Used for UI tests.
   *
   * @throws IOException if there is an error saving the file
   */
  public static void saveScreenshot() throws IOException {
    final File screenshotDir = new File("screenshots");
    if (!screenshotDir.exists()) {
      if (!screenshotDir.mkdirs()) {
        throw new RuntimeException("Cannot make directories "
            + screenshotDir);
      }
    }

    final File screenshot = File.createTempFile("fll", ".png", screenshotDir);
    LOGGER.error("Screenshot saved to "
        + screenshot.getAbsolutePath());
    // file can't exist when calling save desktop as png
    if (screenshot.exists()
        && !screenshot.delete()) {
      throw new RuntimeException("Cannot delete screenshot file "
          + screenshot);
    }
    SCREENSHOT_TAKER.saveDesktopAsPng(screenshot.getAbsolutePath());
  }

  /**
   * Set the running head to head tournament parameter. Make sure that the current
   * tournament is set before calling this method.
   *
   * @param selenium the web driver
   * @param runningHeadToHead the value of running head to head
   * @throws InterruptedException see {@link #loadPage(WebDriver, String)}
   * @throws IOException see {@link #loadPage(WebDriver, String)}
   */
  public static void setRunningHeadToHead(final WebDriver selenium,
                                          final boolean runningHeadToHead)
      throws IOException, InterruptedException {
    loadPage(selenium, TestUtils.URL_ROOT
        + "admin/edit_tournament_parameters.jsp");

    final WebElement element = selenium.findElement(By.id("running_head_to_head"));
    if (runningHeadToHead != element.isSelected()) {
      element.click();
    }

    selenium.findElement(By.id("submit")).click();

    assertNotNull(selenium.findElement(By.id("success")));
  }

}
