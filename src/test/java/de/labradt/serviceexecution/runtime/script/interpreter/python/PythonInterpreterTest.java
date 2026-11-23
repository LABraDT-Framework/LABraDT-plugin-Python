package de.labradt.serviceexecution.runtime.script.interpreter.python;

import de.labradt.serviceexecution.core.api.enums.ExecutionCategory;
import de.labradt.serviceexecution.core.api.enums.TechnologyAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonInterpreterTest {

    private static PythonInterpreter interpreter;

    @BeforeAll
    static void setUp() throws Exception {
        interpreter = new PythonInterpreter("", 30);
        boolean available;
        try {
            available = "ok".equals(interpreter.execute("print('ok')", Map.of()));
        } catch (Exception e) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Python is not installed");
    }

    @Test
    void definesTheTechnologyScriptPython() {
        assertSame(PythonInterpreter.PYTHON, TechnologyAdapter.valueOf("python"));
        assertSame(ExecutionCategory.SCRIPT, PythonInterpreter.PYTHON.executionCategory);
        assertSame(PythonInterpreter.PYTHON, interpreter.getScriptType());
    }

    @Test
    void runsAScriptWithArgumentsAsEnvironment() throws Exception {
        String out = interpreter.execute("import os\nprint(int(os.environ['A']) + int(os.environ['B']))", Map.of("A", "1", "B", "2"));
        assertEquals("3", out);
    }

    @Test
    void runsAPackedProjectFolder() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            put(out, "__main__.py", "import runpy\nrunpy.run_module('main', run_name='__main__')\n");
            put(out, "main.py", "from helpers.calc import twice\nimport os\nprint(twice(int(os.environ['X'])))\n");
            put(out, "helpers/__init__.py", "");
            put(out, "helpers/calc.py", "def twice(x):\n    return 2 * x\n");
        }
        String stored = PythonInterpreter.PYZ_HEADER + "\n" + Base64.getMimeEncoder().encodeToString(zip.toByteArray());
        assertEquals("42", interpreter.execute(stored, Map.of("X", "21")));
    }

    @Test
    void reportsFailures() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> interpreter.execute("raise SystemExit('broken')", Map.of()));
        assertTrue(e.getMessage().contains("broken"), e.getMessage());
    }

    private static void put(ZipOutputStream out, String name, String content) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
