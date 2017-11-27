package org.web3j.mavenplugin.solidity;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compiles the given Solidity Contracts into binary code.
 * <p>
 * Inspired by https://github.com/ethereum/ethereumj/tree/develop/ethereumj-core/src/main/java/org/ethereum/solidity
 */
public class SolidityCompiler {

    private SolC solc;

    private Log LOG;

    private static SolidityCompiler INSTANCE;

    private Triple<Integer, Integer, Integer> version;

    private SolidityCompiler(Log log) {
        this.LOG = log;
        Triple<Integer, Integer, Integer> local_version = solidityVersion();
        if (local_version == null) {
            LOG.info("Solidity Compiler from library is used");
            solc = new SolC();
            version = solidityVersion();
        } else {
            version = local_version;
        }
    }

    public static SolidityCompiler getInstance(Log log) {
        if (INSTANCE == null) {
            INSTANCE = new SolidityCompiler(log);
        }
        return INSTANCE;
    }

    public CompilerResult compileSrc(
            byte[] source, SolidityCompiler.Options... options) {
        return compileSrc(source, null, null, options);
    }

    public CompilerResult compileSrc(
            byte[] source, Path basePath, Path[] allowedImportPaths, SolidityCompiler.Options... options) {


        boolean success = false;
        String error = "";
        String output = "";

        Map<String, String> environment = new HashMap<>();
        String canonicalSolCPath;
        if (solc == null) {
            canonicalSolCPath = "solc";
        } else {
            canonicalSolCPath = solc.getCanonicalPath();
            environment.put("LD_LIBRARY_PATH", solc.getCanonicalWorkingDirectory());
        }

        List<String> commandParts = prepareCommandOptions(canonicalSolCPath, basePath, allowedImportPaths, options);

        CommandLine commandLine = CommandLine.parse(commandParts.stream().reduce((a, b) -> a + " " + b).get());
        LOG.debug(commandLine.toString());

        File executionDir = allowedImportPaths != null ? basePath.toAbsolutePath().toFile() : null;

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (InputStream stdin = new ByteArrayInputStream(source);){

            Executor exec = new DefaultExecutor();
            exec.setWorkingDirectory(executionDir);
            exec.setStreamHandler(new PumpStreamHandler(stdout, stderr, stdin));

            success = exec.execute(commandLine, environment) == 0;

        } catch (IOException e) {
            StringWriter errorWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(errorWriter));
            error = errorWriter.toString();
        } finally {
            IOUtils.closeQuietly(stdout);
            IOUtils.closeQuietly(stderr);
            output += stdout.toString();
            error += stderr.toString();
        }

        return new CompilerResult(error, output, success);
    }

    private List<String> prepareCommandOptions(String canonicalSolCPath, Path basePath, Path[] allowedImportPaths, SolidityCompiler.Options... options) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(canonicalSolCPath);

        //Allow-paths was added in 0.4.11, which is not in our native bundled version
        if (version.compareTo(Triple.of(0, 4, 11)) > 0) {
            if (allowedImportPaths != null) {
                for (Path allowedImportPath : allowedImportPaths) {
                    commandParts.add("--allow-paths=" + allowedImportPath.toAbsolutePath().toString() + "/");
                }
            }
        }

        if (basePath == null) {
            commandParts.add(" =./");
        }

        commandParts.add("--optimize");
        commandParts.add("--overwrite");
        commandParts.add("--combined-json");

        commandParts.add(Arrays.stream(options).map(option -> option.toString()).collect(Collectors.joining(",")));

        //This was nessesary to read from stdIn when we added the basepath mappings above...
        commandParts.add("-");
        return commandParts;
    }

    public Triple<Integer, Integer, Integer> solidityVersion() {
        try {
            String solcPath;
            if (solc != null) {
                solcPath = solc.getCanonicalPath();
            } else {
                solcPath = "solc";
            }
            CommandLine commandLine = CommandLine.parse(solcPath + " --version");
            DefaultExecutor exec = new DefaultExecutor();

            ByteArrayOutputStream stdoutAndErr = new ByteArrayOutputStream();
            exec.setStreamHandler(new PumpStreamHandler(stdoutAndErr));

            boolean success = exec.execute(commandLine) == 0;

            if (success) {
                String output = stdoutAndErr.toString();
                LOG.info("Solidity Compiler found in "+ Paths.get(solcPath).toAbsolutePath().toString());

                Pattern pattern = Pattern.compile(".*((\\d+)\\.(\\d+)\\.(\\d+)).*");
                for (String line : output.split("\n")) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        return Triple.of(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
                    }
                }
            }
        } catch (IOException ignored) {
            LOG.info("Solidity Compiler not installed.");

        }
        return null;
    }

    public enum Options {
        BIN("bin"),
        INTERFACE("interface"),
        ABI("abi"),
        METADATA("metadata"),;

        private String name;

        Options(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }


}
