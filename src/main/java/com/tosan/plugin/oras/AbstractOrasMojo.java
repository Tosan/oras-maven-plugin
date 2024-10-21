package com.tosan.plugin.oras;

import com.tosan.plugin.oras.util.OCIRegistry;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.MatchPatterns;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.*;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Saeed Hashemi
 * @since 25/08/2022
 */
@Getter
@Setter
public abstract class AbstractOrasMojo extends AbstractMojo {
    protected static final String LOGIN_TEMPLATE = "login -u %s %s --password-stdin";
    protected static final String PUSH_TEMPLATE = "%s/%s:%s %s";

    private final Clock clock = Clock.systemDefaultZone();

    @Parameter(property = "oras.executableDirectory")
    private String executableDirectory;

    @Parameter(property = "oras.workingDirectory", defaultValue = "${project.build.directory}")
    private String workingDirectory;

    @Parameter(property = "oras.artifacts", required = true)
    private String[] artifacts;

    @Parameter(property = "oras.artifactType")
    private String artifactType;

    @Parameter(property = "oras.excludes")
    private String[] excludes;

    @Parameter(property = "oras.uploadName", defaultValue = "${project.artifactId}")
    private String uploadName;

    @Parameter(property = "oras.uploadVersion", defaultValue = "${project.version}")
    private String uploadVersion;

    @Parameter(property = "oras.outputDirectory", defaultValue = "${project.build.directory}/oras")
    private String outputDirectory;

    @Parameter(property = "oras.archiveName", defaultValue = "${project.artifactId}-${project.version}.tgz")
    private String archiveName;

    @Parameter(property = "oras.timestampOnSnapshot")
    private boolean timestampOnSnapshot;

    @Parameter(property = "oras.timestampFormat", defaultValue = "yyyyMMddHHmmss")
    private String timestampFormat;

    @Parameter(property = "oci.stable")
    private OCIRegistry stableRepository;

    @Parameter(property = "oci.snapshot")
    private OCIRegistry snapshotRepository;

    @Parameter(property = "oci.security", defaultValue = "~/.m2/settings-security.xml")
    private String ociSecurity;

    @Component(role = org.sonatype.plexus.components.sec.dispatcher.SecDispatcher.class, hint = "default")
    private SecDispatcher securityDispatcher;

    @Parameter(property = "oras.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "oras.debug", defaultValue = "false")
    private boolean debug;

    @Parameter(property = "oras.verbose", defaultValue = "false")
    private boolean verbose;

    @Parameter(property = "oras.insecure", defaultValue = "false")
    private boolean insecure;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Parameter(defaultValue = "${project.groupId}", readonly = true)
    private String projectGroupId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    Path getOrasExecutablePath() throws MojoExecutionException {
        String orasExecutable = SystemUtils.IS_OS_WINDOWS ? "oras.exe" : "oras";
        Optional<Path> path;

        if (StringUtils.isEmpty(executableDirectory)) {
            path = findInPath(orasExecutable);
        } else {
            path = Optional.of(Paths.get(executableDirectory, orasExecutable))
                    .map(Path::toAbsolutePath)
                    .filter(Files::exists);
        }

        return path.orElseThrow(() -> new MojoExecutionException("Oras executable is not found."));
    }

    private String getCommandFlags(String command) {
        String flags = "";

        //common flags:
        if (debug) {
            flags += " --debug";
        }
        if (verbose) {
            flags += " --verbose";
        }
        if (insecure) {
            flags += " --insecure";
        }

        //push flags:
        if (command.equals("push")) {
            if (StringUtils.isNotEmpty(artifactType)) {
                flags += " --artifact-type " + artifactType;
            }

        }

        return flags;
    }

    /**
     * Finds the absolute path to a given {@code executable} in {@code PATH} environment variable.
     *
     * @param executable the name of the executable to search for
     * @return the absolute path to the executable if found, otherwise an empty optional.
     */
    private Optional<Path> findInPath(String executable) {
        String[] paths = getPathFromEnvironmentVariables();
        return Stream.of(paths)
                .map(Paths::get)
                .map(path -> path.resolve(executable))
                .filter(Files::exists)
                .map(Path::toAbsolutePath)
                .findFirst();
    }

    String[] getPathFromEnvironmentVariables() {
        return System.getenv("PATH").split(Pattern.quote(File.pathSeparator));
    }

    void checkArtifacts(String[] artifacts) throws MojoFailureException, FileNotFoundException {
        for (String artifact : artifacts) {
            if (Paths.get(artifact).isAbsolute()) {
                throw new MojoFailureException("Absolute artifact is not allowed: " + artifact);
            }
            if (Files.notExists(Paths.get(getWorkingDirectory(), artifact).toAbsolutePath())) {
                throw new FileNotFoundException("Artifact not found: " + artifact);
            }
        }
    }

    void oras(String command, String arguments, String errorMessage) throws MojoExecutionException {
        oras(command, arguments, errorMessage, null);
    }

    void oras(String command, String arguments, String errorMessage, String stdin) throws MojoExecutionException {
        String fullCommand = getOrasExecutablePath() + " " + command + " " + getCommandFlags(command) + " " + arguments;

        // execute oras
        getLog().debug(fullCommand);
        int exitValue;
        try {
            Process p = Runtime.getRuntime().exec(fullCommand, null, new File(getWorkingDirectory()));
            new Thread(() -> {
                if (StringUtils.isNotEmpty(stdin)) {
                    try (OutputStream outputStream = p.getOutputStream()) {
                        outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException ex) {
                        getLog().error("failed to write to stdin of oras", ex);
                    }
                }

                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String inputLine;
                try {
                    while ((inputLine = input.readLine()) != null) {
                        getLog().info(inputLine);
                    }
                } catch (IOException e) {
                    getLog().error(e);
                }
            }).start();
            new Thread(() -> {
                BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String errorLine;
                try {
                    while ((errorLine = error.readLine()) != null) {
                        getLog().error(errorLine);
                    }
                } catch (IOException e) {
                    getLog().error(e);
                }
            }).start();
            p.waitFor();
            exitValue = p.exitValue();
        } catch (Exception e) {
            getLog().error("Error processing command [" + fullCommand + "]", e);
            throw new MojoExecutionException("Error processing command", e);
        }

        if (exitValue != 0) {
            throw new MojoExecutionException(errorMessage);
        }
    }

    List<String> getArtifactsDirectories(String path) throws MojoExecutionException {
        List<String> exclusions = new ArrayList<>();
        if (getExcludes() != null) {
            exclusions.addAll(Arrays.asList(getExcludes()));
        }
        exclusions.addAll(FileUtils.getDefaultExcludesAsList());

        MatchPatterns exclusionPatterns = MatchPatterns.from(exclusions);
        try (Stream<Path> files = Files.walk(Paths.get(path), FileVisitOption.FOLLOW_LINKS)) {
            List<String> artifactDirs = files.map(p -> p.getParent().toString())
                    .filter(shouldIncludeDirectory(exclusionPatterns))
                    .collect(Collectors.toList());

            if (artifactDirs.isEmpty()) {
                getLog().warn("No artifacts detected - no files found below " + path);
            }

            return artifactDirs;
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to scan artifacts directory at " + path, e);
        }
    }

    private Predicate<String> shouldIncludeDirectory(MatchPatterns exclusionPatterns) {
        return inputDirectory -> {
            boolean isCaseSensitive = false;
            boolean matches = exclusionPatterns.matches(inputDirectory, isCaseSensitive);

            if (matches) {
                getLog().debug("Skip excluded directory " + inputDirectory);
                return false;
            }
            return true;
        };
    }

    OCIRegistry getUploadRepo() {
        if (uploadVersion != null && uploadVersion.endsWith("-SNAPSHOT")
                && snapshotRepository != null && StringUtils.isNotEmpty(snapshotRepository.getUrl())) {
            return snapshotRepository;
        }
        if (stableRepository != null && StringUtils.isNotEmpty(stableRepository.getUrl())) {
            return stableRepository;
        }
        throw new IllegalArgumentException("there is no oci repo.");
    }

    /**
     * authenticate to oci repo. If username is not provided the repo
     * name will be used to search for credentials in <code>settings.xml</code>.
     *
     * @param registry oci repo with id and optional credentials.
     * @throws IllegalArgumentException Unable to get authentication because of misconfiguration.
     * @throws MojoExecutionException   Unable to get password from settings.xml
     */
    void authenticate(OCIRegistry registry) throws MojoExecutionException {
        PasswordAuthentication authentication = getAuthentication(registry);
        if (authentication != null) {
            String arguments = String.format(LOGIN_TEMPLATE, authentication.getUserName(), registry.getUrl());
            oras(arguments, "", "can't login to registry", new String(authentication.getPassword()));
        }
    }

    private PasswordAuthentication getAuthentication(OCIRegistry registry)
            throws IllegalArgumentException, MojoExecutionException {
        String id = registry.getName();

        if (registry.getUsername() != null) {
            if (registry.getPassword() == null) {
                throw new IllegalArgumentException("Repo " + id + " has a username but no password defined.");
            }
            getLog().debug("Repo " + id + " has credentials defined, skip searching in server list.");
            return new PasswordAuthentication(registry.getUsername(), registry.getPassword().toCharArray());
        }

        Server server = settings.getServer(id);
        if (server == null) {
            getLog().debug("No credentials found for " + id + " in configuration or settings.xml server list.");
            return null;
        }
        if (server.getUsername() == null || server.getPassword() == null) {
            throw new IllegalArgumentException("Repo " + id + " was found in server list but has no username/password.");
        }
        getLog().debug("Use credentials from server list for " + id + ".");

        try {
            return new PasswordAuthentication(server.getUsername(),
                    getSecDispatcher().decrypt(server.getPassword()).toCharArray());
        } catch (SecDispatcherException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    protected SecDispatcher getSecDispatcher() {
        if (securityDispatcher instanceof DefaultSecDispatcher) {
            ((DefaultSecDispatcher) securityDispatcher).setConfigurationFile(getOciSecurity());
        }
        return securityDispatcher;
    }

    protected String getArchiveVersionWithProcessing() {
        if (isTimestampOnSnapshot() && uploadVersion.endsWith("-SNAPSHOT")) {
            return uploadVersion.replace("SNAPSHOT", getCurrentTimestamp());
        }
        return uploadVersion;
    }

    protected String getCurrentTimestamp() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(getTimestampFormat());
        LocalDateTime currentTime = LocalDateTime.now(clock);
        return dateTimeFormatter.format(currentTime);
    }
}
