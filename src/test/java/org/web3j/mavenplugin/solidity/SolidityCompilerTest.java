package org.web3j.mavenplugin.solidity;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SolidityCompilerTest {

    private SolidityCompiler solidityCompiler;

    @Test
    public void compileContract() throws Exception {
        byte[] source = Files.readAllBytes(Paths.get("src/test/resources/Greeter.sol"));

        CompilerResult compilerResult = solidityCompiler.compileSrc(source, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);

        assertFalse(compilerResult.errors, compilerResult.isFailed());
        assertTrue(compilerResult.errors, compilerResult.errors.isEmpty());
        assertFalse(compilerResult.output.isEmpty());

        assertTrue(compilerResult.output.contains("\"greeter\""));
    }

    @Before
    public void loadCompiler() {
        solidityCompiler = SolidityCompiler.getInstance(new SystemStreamLog());
    }

    @Test
    public void invalidContractVersion() throws Exception {
        byte[] source = Files.readAllBytes(Paths.get("src/test/resources/Greeter-invalid-version.sol"));

        CompilerResult compilerResult = solidityCompiler.compileSrc(source, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);

        assertTrue(compilerResult.isFailed());
        assertFalse(compilerResult.errors.isEmpty());
        assertTrue(compilerResult.output.isEmpty());
    }

    @Test
    public void invalidContractSyntax() throws Exception {
        byte[] source = Files.readAllBytes(Paths.get("src/test/resources/Greeter-invalid-syntax.sol"));

        CompilerResult compilerResult = solidityCompiler.compileSrc(source, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);

        assertTrue(compilerResult.isFailed());
        assertFalse(compilerResult.errors.isEmpty());
        assertTrue(compilerResult.output.isEmpty());
    }

    @Test
    public void twoFileImport() throws Exception {
        byte[] parent = Files.readAllBytes(Paths.get("src/test/resources/Parent.sol"));

        SolidityCompiler solidityCompiler = SolidityCompiler.getInstance(new SystemStreamLog());
        Path src_test_resources = Paths.get("src/test/resources/");
        CompilerResult compilerResult = solidityCompiler.compileSrc(parent,
                src_test_resources,
                new Path[]{src_test_resources},
                SolidityCompiler.Options.ABI,
                SolidityCompiler.Options.BIN);

        assertFalse(compilerResult.errors, compilerResult.isFailed());
        assertTrue(compilerResult.errors, compilerResult.errors.isEmpty());
        assertFalse(compilerResult.output.isEmpty());

        assertTrue(compilerResult.output.contains("\"Child.sol:child\""));
        assertTrue(compilerResult.output.contains("\"parent\""));
    }

    @Test
    public void solcVersion() {

        SolidityCompiler solidityCompiler = SolidityCompiler.getInstance(new SystemStreamLog());

        Triple<Integer, Integer, Integer> solidityVersion = solidityCompiler.solidityVersion();
        assertTrue(solidityVersion.toString() + " is to old, must be at least 0.4.10", solidityVersion.compareTo(Triple.of(0,4,10))>= 0);

    }





}