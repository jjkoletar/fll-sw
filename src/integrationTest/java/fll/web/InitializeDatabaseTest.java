package fll.web;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

import fll.TestUtils;
import fll.util.LogUtils;

/**
 * Test initializing the database via the web.
 */
@ExtendWith(TestUtils.InitializeLogging.class)
@ExtendWith(IntegrationTestUtils.TomcatRequired.class)
public class InitializeDatabaseTest {

  private static final Logger LOGGER = LogUtils.getLogger();

  @Test
  public void testInitializeDatabase(final WebDriver selenium) throws IOException, InterruptedException {
    try (InputStream challengeStream = InitializeDatabaseTest.class.getResourceAsStream("data/challenge-ft.xml")) {
      IntegrationTestUtils.initializeDatabase(selenium, challengeStream);
    } catch (final IOException | RuntimeException | AssertionError e) {
      LOGGER.fatal(e, e);
      IntegrationTestUtils.storeScreenshot(selenium);
      throw e;
    }
  }
}
