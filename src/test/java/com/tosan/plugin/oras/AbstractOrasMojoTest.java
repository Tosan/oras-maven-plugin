package com.tosan.plugin.oras;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static java.nio.file.Files.write;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
public class AbstractOrasMojoTest {
    private static final LocalDate LOCAL_DATE = LocalDate.of(2022, 1, 1);

    @Spy
    @InjectMocks
    private NoopOrasMojo subjectSpy = new NoopOrasMojo();
    private Path testPath;
    private Path testOrasExecutablePath;

    @Mock
    private Clock clock;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        Clock fixedClock = Clock.fixed(LOCAL_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        doReturn(fixedClock.instant()).when(clock).instant();
        doReturn(fixedClock.getZone()).when(clock).getZone();

        testPath = Files.createTempDirectory("test").toAbsolutePath();
        testOrasExecutablePath = testPath.resolve(SystemUtils.IS_OS_WINDOWS ? "oras.exe" : "oras");
    }

    @Nested
    class WhenUseLocalBinary {

        @BeforeEach
        void setUp() {
            doReturn(new String[]{testPath.toAbsolutePath().toString()}).when(subjectSpy).getPathFromEnvironmentVariables();
        }

        @Test
        void orasIsDetectedFromPATH() throws MojoExecutionException, IOException {
            Path expectedPath = addOrasToTestPath();
            assertEquals(expectedPath, subjectSpy.getOrasExecutablePath());
        }

        @Test
        void executionFailsWhenOrasIsNotFoundInPATH() {
            MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                    subjectSpy::getOrasExecutablePath);
            assertTrue(exception.getMessage().contains("not found"));
        }
    }

    @Nested
    class WhenExecutableDirectoryIsSpecified {

        @BeforeEach
        void setUp() {
            subjectSpy.setExecutableDirectory(testPath.toString());
        }

        @Test
        void orasIsInTheExplicitlyConfiguredDirectory() throws MojoExecutionException, IOException {
            Path expectedPath = addOrasToTestPath();
            assertEquals(expectedPath, subjectSpy.getOrasExecutablePath());
        }

        @Test
        void executionFailsWhenOrasIsNotFoundInConfiguredDirectory() {
            MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                    subjectSpy::getOrasExecutablePath);
            assertTrue(exception.getMessage().contains("not found"));
        }
    }

    private Path addOrasToTestPath() throws IOException {
        return write(testOrasExecutablePath, new byte[]{});
    }

    @AfterEach
    void tearDown() {
        deleteQuietly(testPath.toFile());
    }

    private static class NoopOrasMojo extends AbstractOrasMojo {

        @Override
        public void execute() { /* Noop. */ }
    }
}
