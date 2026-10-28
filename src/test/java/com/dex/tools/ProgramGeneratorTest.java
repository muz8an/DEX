package com.dex.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProgramGeneratorTest {

    @Test
    public void testIsCodeGenerationRequest() {
        assertTrue(ProgramGenerator.isCodeGenerationRequest("write a program to add two numbers"));
        assertTrue(ProgramGenerator.isCodeGenerationRequest("write a python program to add two numbers"));
        assertTrue(ProgramGenerator.isCodeGenerationRequest("write any program in that vs code"));
        assertTrue(ProgramGenerator.isCodeGenerationRequest("write any program"));
        assertTrue(ProgramGenerator.isCodeGenerationRequest("create a python script to calculate fibonacci"));
        assertTrue(ProgramGenerator.isCodeGenerationRequest("code to add two numbers"));

        assertFalse(ProgramGenerator.isCodeGenerationRequest("what time is it"));
        assertFalse(ProgramGenerator.isCodeGenerationRequest("open calculator"));
        assertFalse(ProgramGenerator.isCodeGenerationRequest("play any music"));
    }

    @Test
    public void testGenerateAddTwoNumbersPython() {
        ProgramGenerator.GeneratedProgram prog = ProgramGenerator.generate("write a program to add two numbers");
        assertNotNull(prog);
        assertTrue(prog.getFilename().endsWith(".py"));
        assertTrue(prog.getCode().contains("def add_numbers") || prog.getCode().contains("input"));
        assertTrue(prog.getCode().contains("+"));
    }

    @Test
    public void testGenerateAddTwoNumbersC() {
        ProgramGenerator.GeneratedProgram prog = ProgramGenerator.generate("write a program in c to add two numbers");
        assertNotNull(prog);
        assertEquals("add_two_numbers.c", prog.getFilename());
        assertTrue(prog.getCode().contains("#include <stdio.h>"));
        assertTrue(prog.getCode().contains("scanf"));
    }

    @Test
    public void testGenerateAddTwoNumbersWithCustomFilename() {
        ProgramGenerator.GeneratedProgram prog = ProgramGenerator.generate("write a program to add two numbers in test.py");
        assertNotNull(prog);
        assertEquals("test.py", prog.getFilename());
        assertTrue(prog.getCode().contains("add"));
    }

    @Test
    public void testGenerateGeneralStarterProgram() {
        ProgramGenerator.GeneratedProgram prog = ProgramGenerator.generate("write any program in that vs code");
        assertNotNull(prog);
        assertEquals("test.py", prog.getFilename());
        assertTrue(prog.getCode().contains("def main"));
    }

    @Test
    public void testGenerateCalculatorProgram() {
        ProgramGenerator.GeneratedProgram prog = ProgramGenerator.generate("write a calculator program in python");
        assertNotNull(prog);
        assertEquals("calculator.py", prog.getFilename());
        assertTrue(prog.getCode().contains("calculate"));
    }
}
