---
status: accepted
date: 2026-10-23
decision-makers: João Pedro Magalhães
---

# 0059 Convert the inherited Java to Kotlin

## Context and Problem Statement

The Android app still contains inherited Moonlight Java under `apps/android/app/src/main/java/com/limelight`.
[ADR 0004](0004-kotlin-for-new-android-code.md) kept upstream Java untouched, and
[ADR 0054](0054-declare-independence-from-moonlight.md) made that code Borealis code that may be migrated.
Sprints 12 to 14 convert it area by area, so that one language, null safety and the static analysis cover
all of the code that ships. How should the Java be converted without changing behaviour, breaking the
Java code that remains, or losing the history of each file?

## Decision Drivers

* Behaviour stays the same. A conversion is not a place for fixes or features.
* The Java that stays (`Game`, `binding/input`, `binding/video`, `binding/audio` and `MoonBridge`) must
  keep compiling and keep its JNI signatures, without edits.
* `git log --follow` and `git blame` must keep the Java history of each file.
* The persistent formats of the app must keep reading the data that users already have.
* Code that is converted must count in the static analysis and in coverage from the first commit.

## Considered Options

* Convert faithfully, rename first, and keep the Java-facing API (chosen).
* Convert and fix the bugs or change the behaviour in the same commits.
* Use a detekt baseline to hide the findings that the conversion brings in.
* Rename and convert in one commit.
* Keep the inherited code as Java and add Kotlin only around it.

## Decision Outcome

Chosen option: "Convert faithfully, rename first, and keep the Java-facing API", because it moves the
code to Kotlin without a change in behaviour, keeps the file history, and leaves the Java that stays
without edits. The rules are numbered so that each conversion brief can point to them.

1. **Placement and history.**
   1. Converted files move to `apps/android/app/src/main/kotlin/com/limelight/<same path>/X.kt`.
   2. Each conversion step has a rename-only commit (`chore: move X.java to X.kt for the Kotlin conversion`)
      and then a conversion commit (`refactor: convert X to Kotlin`). The rename commit does not compile.
      This is accepted, so that `git log --follow` and blame keep the Java history. First-parent history
      still builds, because merges use `--no-ff`.
   3. The package and every class name stay the same. `com.limelight` is not renamed, and
      `com.limelight.nvstream.jni.MoonBridge` stays Java.
   4. `// BOREALIS:` markers and upstream licence headers are kept as they are.
   5. Several files may move in one rename commit when the next commit converts exactly those files.
      The conversion commit also makes the Kotlin callers compile.
   6. ktfmt formats the new Kotlin file. The rule against reformatting inherited files applies to the
      Java that stays.
2. **The Java-facing API.** Until the last Java caller is converted, the Kotlin code keeps the API that
   Java uses, so that no Java file needs an edit unless the task brief says so:
   1. public fields use `@JvmField`, with the same name;
   2. constants use `const val`, so that Java can use them as `case` labels;
   3. static members use a `companion object` with `@JvmStatic` or `@JvmField`, or an `object`;
   4. overloads that Java calls with fewer arguments use `@JvmOverloads`, or explicit overloads;
   5. checked exceptions are repeated with `@Throws`;
   6. a Java `getFoo()`/`setFoo()` pair becomes a property `foo`; a method with arguments stays a function;
   7. an interface that Kotlin builds as a lambda becomes a `fun interface`;
   8. nested and inner classes keep their nesting and names; a Java inner class stays `inner`;
   9. abstract classes and interfaces that Java extends or implements keep their exact JVM signatures.
   10. Before and after a conversion, `javap -public` is run on each converted class, and every member that
       a Java caller uses must still be there with the same JVM signature. The comparison goes in the
       conversion report.
3. **Nullability.**
   1. A type is nullable when any caller may pass or receive null, or when the Java code stores or returns
      null on any path. When in doubt, the type is nullable.
   2. Where the Java code dereferences a value and would throw a `NullPointerException`, the Kotlin code
      throws at the same point (`!!` or `checkNotNull`). It is checked that no caller catches the
      `NullPointerException`, and any such caller is reported.
   3. Kotlin callers that relied on platform types get the `?.`, `?:` or `!!` form that keeps their
      behaviour. The commit message lists them.
4. **Semantics that must not drift.**
   1. `Math.round` keeps its half-up rounding. `kotlin.math.round` is not used for it.
   2. Byte and shift arithmetic keep the same wrap-around: `and 0xFF` and `ushr`, and narrowing casts
      after the `Int` arithmetic.
   3. `Integer.parseInt`, `Character.digit` and `String.format((Locale) null, ...)` keep their exact
      behaviour.
   4. `switch` keeps its fall-through and the position of `default`. A `when` keeps the same `else`.
   5. Concurrency is kept as it is: `synchronized` on the same monitor, `@Volatile` and `AtomicInteger`
      where they were used, and the same threads, executors and thread names. A conversion adds no
      coroutines.
   6. `equals` and `hashCode` stay hand-written where they were. A class does not become a `data class`
      where that changes equality, `hashCode` or `toString`.
   7. Static initialisers keep their order. A `companion object` `init` runs when the class loads, and a
      top-level property does not run at the same time, so the order is checked.
   8. Side effects stay where they are. For example, `PreferenceConfiguration.readPreferences` writes
      SharedPreferences while it reads.
   9. Every `LimeLog` call keeps its log text and its level.
5. **Persistent formats stay byte-compatible.** These are `computers4.db` (table `Computers`, with the
   `Addresses` JSON), the `uniqueid` file, `client.crt` (PEM) and `client.key` (binary PKCS#8), the default
   SharedPreferences and `GlPreferences`, the `poster.<applicationId>` provider URIs, the shortcut and
   `Game` intent extras, and the JUL logger name `com.limelight.LimeLog`. A fixture written by the Java
   code is committed in the characterization commit and read by the Kotlin code.
6. **Static analysis.**
   1. Mechanical detekt findings are fixed without a change in behaviour. For example, a magic number
      becomes a named `private const val` with the same value.
   2. The structural rules (`LongMethod`, `CyclomaticComplexMethod`, `CognitiveComplexMethod`,
      `LargeClass`, `TooManyFunctions`, `NestedBlockDepth`, `ReturnCount`, `ComplexCondition` and
      `LongParameterList`) are suppressed on the declaration with `@Suppress("<Rule>")`, next to the comment
      `// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).`
      The comment names the sprint that converted the code: `(Sprint 12 backlog)` for Sprint 12 and
      `(Sprint 13 backlog)` for Sprint 13.
      The suppressions are listed in the conversion report, and a backlog item lists them.
   3. A rule that points at a real bug is never suppressed. The bug is reported instead.
   4. There is no detekt baseline.
7. **Coverage.** Converted code counts in Kover coverage from the first conversion. The exclusion for
   `com.limelight.*` is narrowed to the Java that stays: `com.limelight.Game` and its nested classes (`com.limelight.Game$*`),
   `com.limelight.binding.video.*`, `com.limelight.binding.audio.*` and `com.limelight.nvstream.jni.*`.
   Sprint 13 took `com.limelight.binding.input.*` off the list. In Codecov, converted code is held to the same gate as the rest
   ([ADR 0049](0049-coverage-gate.md): 90% of the patch, and at most 1% project drop). The owner first
   planned separate statuses for the converted path, with a target that would rise each sprint; at the
   end of Sprint 12 the converted code measured 97.0% line coverage, so the single gate was kept
   (owner decision, 2026-10-23). If a later migration sprint cannot reach the gate, the owner decides
   again then. Each migration sprint is merged on its own.
8. **Dead code.** Code that is verified as dead, and that the task brief lists, is removed before the
   conversion. In Sprint 12 these are the three `LegacyDatabaseReader` classes, which read
   `computers.db`, `computers2.db` and `computers3.db`, databases of old Moonlight versions that never
   exist under Borealis's application id `io.github.magalz.borealis`, and unused helpers. Each removal has its own
   `refactor: remove <what>` commit, and the commit message gives the evidence: the callers searched in
   Java, Kotlin, XML, the manifest, reflection and tests. The build passes after each removal.
9. **Commit order and tests.** Each conversion starts with characterization tests (`test: characterize
   <behaviour>`) that pass on the Java code before the conversion and after it. The tests are shown to
   fail when the Java is broken on purpose. The owner's rule of a failing test before the implementation
   applies to behaviour changes, and a conversion has none.
10. **Behaviour changes are out of scope.** A bug found during a conversion is reported and goes to the
    backlog. A conversion does not fix it, rename members unless Kotlin forces it, or add new abstractions,
    coroutines, Flow or Hilt injection.

### Consequences

* Good, because the converted code is covered by the same static analysis and coverage as the Kotlin
  code written in Borealis.
* Good, because the file history is kept: `git log --follow` and blame can follow each file back to
  its Java history.
* Good, because the Java that stays needs no edits, and the JNI signatures stay the same.
* Good, because each conversion is checked against the Java code by characterization tests and by the
  `javap` comparison.
* Good, because the persistent data that users have keeps being read.
* Good, because the detekt findings that a faithful conversion cannot fix are visible as suppressions and a
  backlog item, not hidden by a baseline.
* Bad, because a rename-only commit does not compile. It is accepted, but it breaks `git bisect` at that
  commit.
* Bad, because the Java-facing annotations (`@JvmField`, `@JvmStatic`, `@JvmOverloads`, `@Throws`) stay in
  the Kotlin code until the last Java caller is converted, and some structural code stays as it was.
* Bad, because the suppressions and the backlog items add work in a later sprint.
* Bad, because a faithful conversion keeps bugs that are found until they are fixed in a later sprint.

## Pros and Cons of the Options

### Convert faithfully, rename first, and keep the Java-facing API

* Good, because behaviour does not change, and the characterization tests check it.
* Good, because the history of each file is kept.
* Bad, because a rename-only commit does not compile.
* Bad, because the Java-facing annotations stay until the migration ends.

### Convert and fix the bugs or change the behaviour in the same commits

* Good, because the bugs are fixed sooner.
* Bad, because a regression cannot be told apart from a conversion error, and the characterization tests
  would have to change.

### Use a detekt baseline

* Good, because the build passes at once.
* Bad, because the findings are hidden instead of being fixed or suppressed on the declaration.

### Rename and convert in one commit

* Good, because there is no commit that does not compile.
* Bad, because `git log --follow` and blame lose the Java history of the file.

### Keep the inherited code as Java and add Kotlin only around it

* Good, because nothing in the inherited code changes.
* Bad, because the Java code keeps the static analysis and the null safety of Kotlin out of reach, and
  the migration that ADR 0054 allows does not reach the code that ships.

## More Information

* This ADR updates [ADR 0004](0004-kotlin-for-new-android-code.md). ADR 0004 said that upstream Java stays
  untouched. This ADR, with [ADR 0054](0054-declare-independence-from-moonlight.md), says that the inherited
  Java is Borealis code and is converted under the rules above. The Java that is still in use stays Java
  until Sprints 13 and 14.
* Related: [0054](0054-declare-independence-from-moonlight.md) (inherited code is Borealis code).
* Related: [0049](0049-coverage-gate.md) (the coverage gate that rule 7 amends for converted code).
* Related: [roadmap, Kotlin migration (Sprints 12 to 14)](../roadmap.md#kotlin-migration-sprints-12-to-14).
* Related: [characterization tests for inherited code](../development/tdd.md#characterization-tests-for-inherited-code).
