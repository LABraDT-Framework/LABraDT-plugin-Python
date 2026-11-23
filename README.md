# Python runner plugin

Runs Python services in the LABraDT Runtime. It adds the technology **SCRIPT / PYTHON**: a new language in the fixed category SCRIPT, defined by the plugin itself with

```java
public static final TechnologyAdapter PYTHON = TechnologyAdapter.define("PYTHON", ExecutionCategory.SCRIPT);
```

The categories SCRIPT, CONTAINER and EXTERNAL are fixed by the Runtime. Runner plugins add adapters (languages, tools) inside them in the same way, e.g. `TechnologyAdapter.define("RUBY", ExecutionCategory.SCRIPT)`.

## What a Python service is

The service file in the Engine storage is one of these:

- **A plain Python script.** It is run as `python3 <file>` (`py -3` on Windows).
- **A whole project folder.** The LABraDT UI packs a folder with `main.py` as a zip application: the first line is `# labradt:pyz base64`, followed by the base64 of the `.pyz`, which holds the folder plus an added `__main__.py` that runs `main.py`. Sibling modules and packages can be imported. The Engine storage takes files up to 1 MB.

Service arguments (default and dynamic args) arrive as environment variables. Whatever the script prints to stdout is the service result. A non-zero exit code fails the execution, and stderr goes into the error message.

Packages from `requirements.txt` are **not** installed automatically. Install them into the Python that the Runtime uses, or run the service as a container with the Docker runner instead.

## Settings (Runtime)

| Property | Env var | Default |
|---|---|---|
| `runtime.python.command` | `RUNTIME_PYTHON_COMMAND` | `python3` (`py -3` on Windows) |
| `runtime.python.timeout-seconds` | `RUNTIME_PYTHON_TIMEOUT_SECONDS` | `300` |

## Build and install

```
mvn install          # in runtime-labradt-framework first, then here
```

In the LABraDT UI, go to Services, then Add Service Plugin, and choose this folder or `target/runtime-python-0.0.1-SNAPSHOT.jar`. Restart the Runtime afterwards. The tests (`PythonInterpreterTest`) are skipped when no Python is installed.
