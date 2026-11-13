# Rich process model — Java 21

A separate process-authoring model based on the supplied `model.zip`. See
[DESIGN.md](DESIGN.md) for scope and the planned implementation commits.

```sh
mvn clean install
```

Requires Java 21. Maven uses a pinned compiler plugin with `--release 21` and strict
warnings. No Java library dependencies. The test phase invokes our plain Java
harness under `process-model-checks`; Surefire's JUnit count is not its check count.
