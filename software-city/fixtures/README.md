# Software City examples

`source/` contains synthetic Java inputs. Its Maven/Gradle files are module markers; the analyzer never evaluates them.

`synthetic.snapshot.json` contains nine types from a deterministic fixture commit. Regenerate it with the Java/JGit fixture profile and packaged CLI commands in the project README.

`quarkus-runtime.snapshot.json` contains 219 actual types from [Quarkus](https://github.com/quarkusio/quarkus) commit `866d824ae3cca9750702e8eb4caea636d086ed48`, restricted to `core/runtime/src/main/java/**/*.java`. It includes metrics and locations, not source text. The packaged analyzer generated it in strict mode without executing target source or builds.

`spring-core.snapshot.json` contains 679 actual types from Spring Framework 7.0.9, commit `82a6b40b9366ec181ededdad282b307ee5381a52`, restricted to `spring-core/src/main/java/**/*.java`. It includes checked-in ASM/CGLIB types; Kotlin and other source sets are outside this selection.

The real snapshots contain derived metrics and relative source locations. Quarkus and Spring source code is not bundled. The project README shows how to check out their pinned revisions outside the companion repository and regenerate the JSON.
