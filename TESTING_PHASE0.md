# Phase 0 — Stabilising the AutoFirma Test Harness

The default build now passes offline, on a machine with no network, no smartcard and no
display — and so does the full application reactor, which had never reached its last ten
modules before.

| | |
|---|---|
| **Branch** | `claude/autofirma-testing-phase0-harness` |
| **Commits** | 8 (plus this report) |
| **Measured on** | JDK 21 / Maven 3.9, offline |

---

## Were the Phase 0 goals achieved?

**Yes** — all four acceptance criteria are met, with one deliberate shortfall: the tests
still carrying `@Ignore` have written reasons but no tracking issues.

| Acceptance criterion | Status | Evidence |
|---|---|---|
| `mvn clean test` green with no network | **Met** | 298 tests, 0 failures, 33 modules — previously a build failure at 26 modules |
| `mvn clean test -P env-dev,autofirma` green with no network | **Met** | 308 tests, 0 failures, 38 modules — previously a build failure at 28 modules |
| Skip count in the unit lane ≈ 0 | **Met** | 91 → 6. The six are genuinely broken or obsolete tests, which the plan permits |
| Every excluded test carries a category | **Met, with a gap** | True for all 72 environment-excluded tests. The plan also asked that each remaining `@Ignore` get a tracking issue; each got a written reason instead, since no issue tracker was in scope |

The plan's own Phase 0 goal — *"a reproducible, green, offline `mvn test` for the full
37-module reactor, with every environment-dependent test carrying a machine-readable
category instead of a comment"* — is satisfied. The reactor is 38 modules because
`afirma-test-support` is new.

---

## Before and after

Both lanes were measured on the same container, offline, before any change and after the
final commit.

| Lane | Modules built | Tests run | Failed | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `mvn clean test` — before | 26 (+4 skipped) | 340 | 2 | 91 | Build failure |
| **`mvn clean test` — after** | **33** | **298** | **0** | **6** | **Build success** |
| `-P env-dev,autofirma` — before | 28 (+7 skipped) | 343 | 2 | 94 | Build failure |
| **`-P env-dev,autofirma` — after** | **38** | **308** | **0** | **11** | **Build success** |

Two things in that table need reading carefully.

**The test count falls** because roughly sixty environment-dependent tests moved from
"executed, then skipped at runtime" to "never selected" — category exclusion happens
before execution, which is also why skips drop from 91 to 6.

**The module count rises**, and that matters more. Both lanes used to abandon four and
seven modules downstream of the two failing ones, so those modules' tests had never run at
all. That is why the full lane's total goes *up* despite the exclusions.

---

## The work: plan items 1–7

### 1. Pin and configure Surefire — done

Version pinned to 3.2.5 in the root `pluginManagement`, with headless AWT, UTF-8,
`trimStackTrace=false`, a temporary `rerunFailingTestsCount=1`, and the `excludedGroups`
list that drives the whole category scheme.

### 2. Declare JUnit explicitly — done

16 modules gained an explicit test-scope `junit` dependency. They had been compiling their
tests only through SpongyCastle's transitive copy, so any dependency bump would have broken
them silently.

### 3. Bootstrap `afirma-test-support` — done

A new module holding seven `@Category` marker interfaces and nothing else, built first in
every profile that compiles tests, and consumed by 32 modules. An `integration-tests`
profile empties the exclusion list to run the categorised tests:

```
mvn test -P env-dev,integration-tests
mvn test -P env-dev,autofirma,integration-tests
```

### 4. Reclassify instead of ignoring — done

72 `@Category` annotations across 51 test classes now record what each test needs, in place
of a Spanish comment.

| Category | Tests |
|---|---:|
| `RequiresNetwork` | 20 |
| `RequiresGui` | 18 |
| `RequiresTriphaseServer` | 17 |
| `RequiresSmartCard` | 8 |
| `RequiresWindows` | 7 |
| `RequiresNss` | 4 |
| `RequiresMacOS` | 2 |

### 5. Quarantine the two red tests — done

`TestXAdES#testSignExternallyDetached` (live HTTP to `estaticos.redsara.es`) is now
`RequiresNetwork`; `TestRFC2254CertificateFilter`'s recursive-filter test (Firefox NSS) is
now `RequiresNss`. These two were what broke the reactor.

### 6. Convert the `main()` drivers — done

Twelve classes held no `@Test` at all. Three became real asserting unit tests
(`TestPathShortener`, `TestShortName`, `TestBinParser`); the other nine became categorised
tests. None were deleted in the end. Two helper classes with no `@Test`
(`TimestampsAnalyzer`, `TestContants`) were left alone because live tests reference them.

Three were made safer than the originals rather than transcribed: `TestFirewall` had a
`System.exit(-2)` on its failure path and a `Thread.sleep(3000)` race; `TestWaitForConnection`
waited for a connection forever; `TestBatchSigner` had developer paths baked into base64
config blobs.

### 7. Remove hardcoded paths — done

Developer paths such as `C:\Users\carlos.gamuci\Desktop` and `C:\Entrada` are gone from the
in-scope test sources; fixtures load from the classpath and outputs go to temp directories.
Windows paths that are the actual subject of a categorised test were kept, since they are
that test's environment requirement rather than a fixture location.

---

## Three failures nobody had ever seen

Fixing the two known-red tests let the reactor reach modules it had been skipping for a long
time. Each of these was pre-existing and latent — none was introduced by this work, and none
could have been noticed while the build stopped earlier.

| Module | Finding |
|---|---|
| `afirma-core-massive` | The production OOXML signer writes the monitor count and screen resolution into the signature, so it throws `HeadlessException`. The combinations loop was split by format, keeping every non-OOXML format in the unit lane. |
| `afirma-simple-plugins` | The module has a `src/test` directory holding one JSON resource and no JUnit, so Surefire ran on it and refused `excludedGroups` outright. A consequence of the new root configuration, fixed by declaring the test dependencies. |
| `afirma-simple` | `Pdf2ImagesConverterTests#testPdfLoadUi` builds a `JFrame` and carried no `@Ignore`, so the reclassification sweep never saw it. Now `RequiresGui`. |

---

## What was held to

- **No production code was touched.** Zero existing `src/main` files changed. The eight new
  `src/main` files are the marker interfaces in the new test-support module.
- **Java 8 preserved.** `source`/`target` remain 1.8, and the new and modified test code uses
  no post-Java-8 API.
- **Both lanes verified, never assumed.** Every reported number comes from a full
  `clean test` run. The category wiring was checked in both directions: the integration
  profile runs a categorised test that the default lane selects zero of.

---

## What is still open

- **No tracking issues.** The plan wanted one per surviving `@Ignore`. There are eleven
  across both lanes — a JDK bug reproducer, deprecated-API tests, a fixture missing from the
  repository, a JDK PKCS#12 limitation, and the `System.exit()` cases. Each has a written
  reason in the source; none has an issue.
- **The integration lane has never run green end to end.** This container has no network, no
  tri-phase server, no smartcard and no display, so only the *selection* mechanism could be
  proven. The categorised tests themselves remain unverified until CI gives them a home.
- **Three tests cannot run in any lane.** `SignFromCommandLineTest`,
  `BatchFromCommandLineTest` and `TestGUISignViewer` all drive `SimpleAfirma.main()`, which
  calls `System.exit()` and would abort the Surefire fork. They carry both a category and an
  `@Ignore`; Phase 5's injectable exit handler is the real fix.
- **`rerunFailingTestsCount=1` is temporary.** It masks a single flake on retry. It should
  come out once CI shows the suite is stable, or it will hide real intermittency.
- **Out-of-scope modules were left as they are.** Uncategorised `@Ignore`s remain in the
  unmaintained applet modules, the server deployables and the AutoFirma plugins — all
  explicitly out of this plan's scope. Five of them did get test dependencies so the
  inherited Surefire configuration cannot break them later.
- **Java 8 compatibility is still unproven.** The build uses `source`/`target` rather than
  `--release`, so compiling on JDK 21 can still link against post-8 APIs and only fail at
  runtime. Phase 1's Temurin 8 job is the guard, and it is the first thing that should land
  next.

---

## Post-review fixes

An independent review of this branch (`TESTING_PHASE0_REVIEW.md`, on
`autofirma-testing-phase0-review`) reproduced every number reported above and confirmed them,
with two corrections to the wording and three items to fix before merge.

Two of the three are applied here. The third — normalising line endings — is **not** applied on
this branch; see "Line endings: still open" below for what was measured and why it was pulled.

### 1. One genuine coverage regression, fixed

`TestKeyStoreWindowsCertACA` carried `@Category(RequiresWindows.class)` at **class** level, which
also excluded `testPkcs12` — a test that loads a PKCS#12 keystore from the classpath, uses no
Windows API, was never `@Ignore`d before this branch and passed on Linux. Categorising it lost
real coverage.

The category now sits on the two methods that genuinely need CAPI (`testStandaloneKeyChain`,
`testMSCapi`); `testPkcs12` is back in the default lane. This is the reason both lane totals below
are one higher than reported earlier.

A method-level comparison of the surefire XML from both branches found this to be the **only**
test that stopped running. In the other direction, 45 tests that had never executed on `master`
now run and pass, so the executed-and-passing total went from 247 to 297 — the headline drop from
340 to 298 counted tests that were selected and then skipped at runtime, which is not coverage.

### 2. The OOXML headless defect is now documented

The `HeadlessException` noted above is a production defect, not a test-environment constraint:
`OOXMLOfficeObjectHelper.getOfficeObject(...)` writes the monitor count, screen resolution and
colour depth into the `SignatureInfoV1` block of every OOXML signature, so signing throws on any
machine with no display — including the command line (`CommandLineLauncher` accepts
`-format ooxml`) and any server-side embedding.

It is written up in **`docs/known-issues/ooxml-headless-signature.md`** with the file:line
evidence, the call path, a reproduction, the captured stack trace and a suggested
`GraphicsEnvironment.isHeadless()`-guarded fix with defaulted metadata. No production code was
changed: Phase 0 does not touch `src/main`, and a change on the signing path needs its own review.
No issue was opened — that has not been authorised.

The four tests categorised `RequiresGui` for this reason now reference the document, so the
category records *why* it exists and that it should be removed when the defect is fixed.

### Line endings: still open

This branch's raw diff against `master` is 9,251/8,514 lines across 106 files, but only 1,111/373
of that is real change — 77 files were converted CRLF→LF as a side effect of being edited, and no
`.gitattributes` exists to pin a convention. That leaves the tree mixed and makes each of those
77 files a whole-file conflict against any other branch touching them.

A whole-repository normalisation was prepared and then **deliberately pulled** before landing, so
this branch still carries the mixed state. It was measured first, and the measurements are worth
keeping for whoever picks this up:

- Normalising every text file converts **1,794 files** CRLF→LF. Verified pure: every change was a
  line-ending change only, with no content difference and no residual CR, and all **975 binary
  fixture blobs were byte-identical** afterwards.
- The cost is that it makes this branch's raw diff against `master` *larger*, not smaller —
  ~335,000 lines across ~1,900 files — because `master` remains CRLF. The whitespace-ignoring
  diff stays small (1,571/373), but the raw figure is what a reviewer meets first.
- Two hazards surfaced that any future attempt must handle. `AfirmaHelp.helpindex` is an Apple
  typedstream binary that Git's automatic text detection classifies as text, so `* text=auto`
  would corrupt it; and `PreferencesPanelFacturaE.java:349` contains a stray lone CR before a
  CRLF, which plain CRLF→LF conversion collapses into a single line ending, silently dropping a
  blank line and leaving a residual CRLF that a later renormalisation would change again.
- The safest design, if this is revisited: default to `* -text` and opt into conversion per
  extension, rather than `* text=auto` with an exclusion list. The repository holds ~975 binary
  fixtures and Git misclassifies several of them (PDFs and `.cer`/`.crt` certificates whose first
  block contains no NUL byte). With that direction a forgotten text extension merely leaves a file
  un-normalised, whereas a forgotten binary one corrupts it. Windows-toolchain sources
  (`.cs`, `.xaml`, `.sln`, `.csproj`, …) should be pinned `eol=crlf` rather than converted, since
  Visual Studio rewrites them back; `*.bat`/`*.cmd`/`*.nsi` stay CRLF and `*.sh`/`*.nsh` stay LF,
  where the line ending is functional.

A narrower option remains available and avoids the raw-diff cost: normalise only the 77 files this
branch already converted, so the branch stops *adding* to the mixed state without repainting the
repository.

### Lane results after these fixes

Both lanes re-run offline on the same container, JDK 21 / Maven 3.9.

| Lane | Modules | Tests | Failures | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `mvn clean test` | 33 | **299** | 0 | 6 | Build success |
| `mvn clean test -P env-dev,autofirma` | 38 | **309** | 0 | 11 | Build success |

Each is one test higher than before, and that one test is `testPkcs12`.

### Still open after this round

The review's remaining findings are deliberately **not** addressed here, since they are either
Phase 1 work or need a decision that is not Phase 0's to make:

- **Line endings.** The tree is still mixed and there is still no `.gitattributes`; see
  "Line endings: still open" above for the measurements and the two hazards a future attempt
  must handle.
- The `integration-tests` profile empties the whole exclusion list at once, so it cannot run green
  on any single machine. Per-category CI lanes are the fix, via
  `-Dsurefire.excluded.groups=…` (a command-line `-D` overrides the POM property — verified).
- The literal `<argLine>` in the root POM will silently disable JaCoCo in Phase 6 unless it becomes
  `@{argLine} …`.
- `afirma-test-support` has no `maven.deploy.skip`, so `env-deploy` would publish it.
- The eleven surviving `@Ignore`s still have written reasons rather than tracked issues.
- `afirma-server-triphase-signer` (`<java.version>1.7</java.version>`) and the `minhap` profile
  (`-Xbootclasspath` to `rt.jar`) cannot build on a modern JDK. Both predate this branch and are
  out of the plan's scope, but they break `env-install`, `sonar` and `minhap` on JDK 21.
