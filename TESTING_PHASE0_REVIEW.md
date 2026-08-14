# Phase 0 Review — AutoFirma Test Harness

Independent review of `claude/autofirma-testing-phase0-harness` (9 commits) against the Phase 0
specification in `TESTING_IMPROVEMENT_PLAN.md` and the implementer's self-report `TESTING_PHASE0.md`.

| | |
|---|---|
| **Reviewed** | `master`…`origin/claude/autofirma-testing-phase0-harness` (106 files) |
| **Environment** | JDK 21.0.10 / Maven 3.9.11, Linux container, no display, no smartcard, no external network |
| **Method** | Every number below was reproduced from a clean build in a dedicated worktree. Nothing was taken from the report on trust. |

---

## Verdict: **accept with follow-ups**

The implementation is sound and the self-report is, with two exceptions noted below, accurate.
Both headline results reproduce **exactly**. No production code was touched. Executed test
coverage went **up** by 50 tests, with exactly one small regression. The single highest-risk item
I was asked to check — whether the new reactor module breaks the untested profiles — came back
clean: all six profiles are wired correctly, and Phase 0 strictly *improves* two of them.

Nothing here blocks Phase 1. One item should be fixed before merge (the diff hygiene), one test
should be un-excluded (a three-line fix), and one production defect needs a tracked bug rather
than a category.

**The five things that matter:**

1. **~88% of the diff is line-ending noise.** 9,251/8,514 insertions/deletions collapse to
   **1,111/373** under `git diff -w --ignore-blank-lines`. 77 of 106 files were converted
   CRLF→LF, and no `.gitattributes` was added. In a repo where 970 of 1,308 `.java` files are
   CRLF, this leaves the tree mixed and will keep re-generating this churn. **Fix before merge**
   (§F1).
2. **Coverage went up, not down — by 50 executed tests — and no profile was broken.** The 340 → 298
   count drop is an artifact of counting runtime-skipped tests; tests that actually *execute and
   pass* went 247 → 297 (§8). Exactly **one** test regressed
   (`TestKeyStoreWindowsCertACA.testPkcs12`, an over-broad class-level category — §F-COV), against
   45 genuinely new passing tests including the previously-unreachable signature-validation and
   tri-phase suites. The report undersells its own result here.
3. **The OOXML `HeadlessException` is a real production defect, not a test-environment problem.**
   `OOXMLOfficeObjectHelper.java:119` embeds monitor count and screen resolution into every OOXML
   signature. AutoFirma ships a headless CLI. Categorising the *test* as `RequiresGui` is the right
   interim move, but it now leaves the entire OOXML signing path untested in every runnable lane
   and quietly encodes a bug as an environment requirement. **Needs a tracked issue** (§F2).
4. **Two report claims are overstated** — "72 environment-excluded tests" is an *annotation*
   count (the real figure is ~122 test methods), and the "before" numbers were measured with
   `-fae` while the table labels the command `mvn clean test` (§4).
5. **The `integration-tests` profile is all-or-nothing** and therefore cannot run green on any
   single machine — it mixes Windows, macOS, smartcard, GUI and network in one lane. The plan
   asked for "the ones the environment supports". The underlying mechanism *does* support
   per-lane selection; it is just undocumented (§F3).

---

## 1. Plan conformance — Phase 0 items 1–7

| # | Plan item | Status | Evidence |
|---|---|---|---|
| 1 | Pin & configure Surefire: version, headless, UTF-8, `trimStackTrace=false`, `rerunFailingTestsCount=1` | **Met** | `pom.xml:177-186` — 3.2.5 pinned via `pom.xml:54`; `argLine` sets `-Djava.awt.headless=true -Dfile.encoding=UTF-8`; all four settings present |
| 2 | Declare `junit:junit` test-scope in every module with tests | **Met** | 16 module poms gained an explicit `junit` dependency (reproduced by diffing `+<artifactId>junit</artifactId>` per pom). Every module with a `src/test` tree now declares both `junit` and `afirma-test-support` — verified exhaustively across all 42 reactor modules |
| 3 | Bootstrap `afirma-test-support` with the 7 category markers; `excludedGroups` by default; `-Pintegration-tests` includes them | **Met, one shortfall** | 7 markers + `package-info` at `afirma-test-support/src/main/java/es/gob/afirma/test/support/`; consumed by 32 modules; `excludedGroups` at `pom.xml:185`. Shortfall: the profile (`pom.xml:647-652`) empties the list *wholesale* rather than enabling "the ones the environment supports" — see §F3 |
| 4 | Reclassify the `@Ignore` sweep into categories; `@Ignore` only for broken/obsolete, **each with a tracking issue** | **Partially met** | 72 `@Category` annotations across 51 classes (reproduced); repo-wide `@Ignore` count 193 → 86. The tracking-issue half was not done — 11 surviving `@Ignore`s carry written reasons only. The report discloses this openly. Acceptable: no issue tracker is in scope for this branch (§F4) |
| 5 | Quarantine the two red tests (`TestXAdES.testSignExternallyDetached`, `TestRFC2254CertificateFilter`) | **Met** | Both reproduced red on `master` (§3) and both now categorised: `TestXAdES.java:785` → `RequiresNetwork`, `TestRFC2254CertificateFilter` → `RequiresNss` |
| 6 | Convert the salvageable `main()` drivers; **delete the rest** in a dedicated commit | **Deviated — justified** | 12 classes converted, **0 deleted**. The plan's own risk section (`TESTING_IMPROVEMENT_PLAN.md:286-288`) prescribes exactly this: "mitigate by converting rather than deleting where there is any doubt". Not scope creep — see §2 |
| 7 | Remove hardcoded `C:\Users\…` paths; fixtures from classpath, temp dirs for output | **Met** | In-scope occurrences 11 → 2. Both survivors are justified: `SimpleTest.java:132` (the Windows path *is* the assertion subject, class is categorised) and `HashFromCommandLineTest.java:20` (in `afirma-simple-plugin-hash`, explicitly out of scope per plan line 9) |

### Acceptance criteria

| Criterion | Status | Reproduced evidence |
|---|---|---|
| `mvn clean test` green offline | **Met** | 33 modules, **298 tests, 0 failures, 6 skipped**, BUILD SUCCESS |
| `mvn clean test -P env-dev,autofirma` green offline | **Met** | 38 modules, **308 tests, 0 failures, 11 skipped**, BUILD SUCCESS |
| Unit-lane skip count ≈ 0 | **Met** | 91 → 6. All six confirmed genuinely broken/obsolete (§3) |
| Every excluded test carries a category | **Met** | No test is excluded by anything other than a category or one of the 11 disclosed `@Ignore`s. The report's phrasing of the count is wrong, not the substance (§4) |

---

## 2. Judgement on the four flagged deviations

**Plan item 6 — nothing deleted. Justified, and the conversions exceed the brief.**
The plan named three salvageable drivers; the implementer produced real asserting tests for
those three *and* usefully converted the other nine instead of deleting them. Three were made
materially safer than their originals, which the report understates:

- `TestFirewall.java:43-64` — replaced a `Thread.sleep(3000)` race with a `CountDownLatch`
  handshake and removed a `System.exit(-2)` that would have killed the Surefire fork.
- `TestWaitForConnection.java:26,43` — an unbounded `accept()` became a 10s bounded wait.
- `TestBatchSigner.java:37-72` — hardcoded `C:/Users/tomas.capote/…` paths inside base64 config
  blobs replaced with `Files.createTempDirectory`, **and** a live `http://www.google.com`
  datasource replaced with an inline base64 literal. The report mentions the paths but not the
  removed network dependency.

This is better than the plan asked for. Keeping the drivers costs nothing: all nine are
categorised out of the default lane.

**Plan item 4 — reasons instead of tracking issues. Acceptable.**
The plan's intent is that no test is silently disabled. A written reason in the source satisfies
that intent; an issue number would satisfy it better but requires a tracker this branch cannot
reach. The report discloses the gap rather than papering over it. Carry it into Phase 1 (§6).

**Double-marking (`@Category` + `@Ignore`) on the three `System.exit()` tests. Correct interim encoding.**
`SignFromCommandLineTest`, `BatchFromCommandLineTest` and `TestGUISignViewer` drive
`SimpleAfirma.main()`, which calls `System.exit()` and would abort the Surefire fork — so
`@Ignore` is doing real work (it stops the integration lane from killing itself), and the
category records the *intended* lane for when Phase 5's injectable `ExitHandler` lands. The two
annotations mean different things here and both are needed. I would keep it.

**`rerunFailingTestsCount=1`. In the plan, and it is doing what the plan feared.**
The mechanism is live and observable — in the integration-lane run, every failing test appears
twice in the log (original + one rerun). See §5 for the flake-hunt result.

---

## 3. Report accuracy — every claim independently reproduced

| Claim in `TESTING_PHASE0.md` | Verdict | My reproduction |
|---|---|---|
| Default lane after: 33 modules, 298 tests, 0 fail, 6 skipped | **Confirmed** | Exact match. BUILD SUCCESS |
| Full lane after: 38 modules, 308 tests, 0 fail, 11 skipped | **Confirmed** | Exact match. BUILD SUCCESS |
| Default lane before: 26 modules (+4 skipped), 340 tests, 2 failures, 91 skipped | **Confirmed, with a methodology caveat** | Exact match — *but only with `-fae`*. Plain `mvn clean test` on master gives 19+1 modules / 288 tests / 1 error / 57 skipped, because it stops at the first failure. See §4 |
| Full lane before: 28 modules (+7 skipped), 343 tests, 2 failures, 94 skipped | **Confirmed, same caveat** | Exact match with `-fae` |
| The two red tests are `TestXAdES.testSignExternallyDetached` and `TestRFC2254CertificateFilter` | **Confirmed** | `TestXAdES.testSignExternallyDetached:803` → HTTP 403 from `estaticos.redsara.es`; `TestRFC2254CertificateFilter:76` → `ClassNotFoundException: MozillaUnifiedKeyStoreManager` |
| "No production code was touched. Zero existing `src/main` files changed." | **Confirmed** | `git diff --stat master…phase0 -- '*/src/main/*'` returns exactly 8 files, all additions (7 markers + `package-info`), 54 insertions, 0 deletions |
| "16 modules gained explicit junit" | **Confirmed** | Exactly 16 |
| "consumed by 32 modules" | **Confirmed** | Exactly 32 consumers |
| "72 `@Category` annotations across 51 test classes" | **Confirmed as stated, but mislabelled** | 72 annotations / 51 classes is right. The per-category table's column header "Tests" is wrong — 27 of the 72 are *class-level*, so the real excluded-test-method count is **~122**, not 72 (§4) |
| "12 main()-driver classes converted, none deleted" | **Confirmed** | 12 classes went 0 `@Test` → ≥1 `@Test`; zero files deleted anywhere in the diff |
| The 6 default-lane skips are "genuinely broken or obsolete" | **Confirmed** | JDK PKCS#12 limitation (1), fixture absent from repo (1), deprecated `Manifest` API (3), JDK-8182580 reproducer (1) |
| `afirma-core-massive`: production OOXML signer throws `HeadlessException` | **Confirmed — and it is a production bug** | `OOXMLOfficeObjectHelper.java:119,127,132,154`, reached from `OOXMLXAdESSigner.java:202` (§F2) |
| `afirma-simple`: `Pdf2ImagesConverterTests#testPdfLoadUi` was an unmarked GUI test | **Confirmed independently** | It is the exact failure that breaks `master`'s `env-install` lane at module 6/12 |
| "Both lanes verified, never assumed" | **Confirmed** | Both lanes reproduce to the test |

---

## 4. Where the report overstates

Two corrections. Neither changes the verdict; both should be fixed in the document.

**(a) "72 environment-excluded tests" conflates annotations with tests.**
`TESTING_PHASE0.md:25` says *"True for all 72 environment-excluded tests"*, and the §4 table
header reads "Tests". 27 of the 72 annotations sit on *classes*, not methods —
`TestAOXAdESTriPhaseSigner.java:34` alone carries 25 `@Test` methods. Counting method-level
annotations plus the methods covered by class-level ones gives **~122 excluded test methods**.
The report understates the scope of its own change by ~40%. (The per-category numbers in the
table — 20/18/17/8/7/4/2 — are internally consistent as annotation counts, including the four
multi-category sites counted once per category.)

**(b) The before/after table compares two different commands.**
`TESTING_PHASE0.md:41-44` labels every row `mvn clean test` and asserts at line 146 that
"Every reported number comes from a full `clean test` run." The "after" rows are plain
`clean test`; the "before" rows only reproduce with `-fae`. This is not cherry-picking — `-fae`
is the *generous* choice, since it lets `master` run 340 tests instead of stopping at 288 — but
the table should say so. Add "`-fae`" to the two "before" rows.

---

## 5. Flake hunt (`rerunFailingTestsCount=1`)

**I could not reproduce the flake, in 7 green runs.** Five consecutive `mvn clean test` runs plus
the two lane runs all produced 298/0/6 with no `Flakes:` section in the output and no
`<flakyFailure>` element in any surefire XML.

Two things worth recording for Phase 1:

- **The mechanism demonstrably works.** In the integration-lane run every failure is reported as
  `Run 1: …` / `Run 2: …`, confirming the single retry fires.
- **"Masks" overstates it slightly.** A retried flake still appears in the build output under a
  `Flakes:` heading and as `<flakyFailure>` in the XML — the build goes green, but the evidence is
  not destroyed. That makes the mitigation cheap: **CI should fail (or at least annotate) the build
  when `Flakes:` appears in the log or any report contains `<flakyFailure>`.** That gets the
  flake-detection benefit without waiting to remove `rerunFailingTestsCount`.

Structurally the default lane looks clean: every test in the tree matching
`Thread.sleep|new Thread|ExecutorService|Math.random|System.currentTimeMillis|new Date()|Calendar.getInstance`
is already categorised out of it, so there is no obvious timing-dependent candidate left running.
The flake the report saw was most likely environmental (a proxy 403 timing out differently) rather
than a genuine race. Phase 1 should keep `rerunFailingTestsCount=1` only until the flake reporting
above is wired, then drop it.

---

## 6. Findings, by severity

### Blocks merge (not Phase 1)

**F1 — 88% of the diff is CRLF→LF conversion, with no `.gitattributes` to stop it recurring.**

| Metric | Value |
|---|---|
| Raw diff | 9,251 insertions / 8,514 deletions, 106 files |
| Under `-w --ignore-blank-lines` | **1,111 / 373** |
| Files converted CRLF→LF | **77 of 106** |
| `.java` files on `master` with CRLF | **970 of 1,308** |
| `.gitattributes` | absent on both branches |

Worst offenders — files where a tiny semantic change produced a huge diff:

| File | Raw lines | Real lines |
|---|---:|---:|
| `afirma-crypto-xades/src/test/java/es/gob/afirma/signers/xades/TestXAdES.java` | 1,903 | **3** |
| `afirma-simple/pom.xml` | 817 | **5** |
| `afirma-crypto-cadestri-client/…/TestCadesTriphase.java` | 436 | **8** |
| `afirma-ui-simple-configurator/…/TestMacCaInstall.java` | 423 | **7** |
| `afirma-ui-core-jse/…/FileDialogsTest.java` | 119 | **5** |
| *(45 files in total have ≤12 real lines but ≥100 raw lines)* |  |  |

*Why it matters.* Reviewers cannot see the 1,111 lines that matter inside 17,765 lines of churn.
Worse, the repo is now **mixed** — 77 files LF, ~893 still CRLF — so every subsequent branch that
touches one of these files will either re-convert it or convert it back, and every one of these
77 files is now a guaranteed whole-file conflict against any other in-flight branch.

*Recommendation.* Before merge, either (a) add a `.gitattributes` (`*.java text eol=lf`,
`*.xml text eol=lf`) and normalise the whole repo in **one dedicated commit**, so the mixed state
is resolved rather than deepened; or (b) rebase the branch preserving original line endings, so
the diff shows only the 1,111 real lines. (a) is the better long-term answer for a
Windows-authored codebase that is now built on Linux CI. Either way this is mechanical — it does
not affect any conclusion about the code.

### Worth fixing before Phase 1 lands

**F-COV — the one genuine coverage regression: an over-broad class-level category.**
`afirma-core-keystores/src/test/java/es/gob/afirma/test/keystores/TestKeyStoreWindowsCertACA.java:31`
applies `@Category(RequiresWindows.class)` to the whole class, but `testPkcs12` (`:93`) is a pure
PKCS#12 classpath test with no Windows API — it was **not** `@Ignore`d on `master` and ran and
passed on Linux. It is now excluded. Full analysis, evidence and the three-line fix in **§8**.
This is the only thing in the entire sweep that is actually wrong, and it is a one-minute fix.

**F2 — A production defect is encoded as a test category, and the OOXML path is now untested everywhere.**

`afirma-crypto-ooxml/src/main/java/es/gob/afirma/signers/ooxml/OOXMLOfficeObjectHelper.java`:
```
:119   final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
:127          ge.getScreenDevices().length          → <Monitors>
:132   final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
:154          ge.getScreenDevices()[0].getDisplayMode().getBitDepth()   → <ColorDepth>
```
called unconditionally from `OOXMLXAdESSigner.java:202`. In a headless JVM every one of these
throws `HeadlessException`, so **AutoFirma cannot sign an OOXML document headlessly at all** —
not in the CLI, not server-side. This is a signing-path defect, not a test problem: the monitor
count and screen resolution are cosmetic Microsoft `SignatureInfoV1` metadata that could be
defaulted (or the block skipped) when `GraphicsEnvironment.isHeadless()`.

Categorising the tests as `RequiresGui` was the right call *for Phase 0* — it is the honest
description of today's behaviour and it unblocked the reactor. But the consequence is that
`TestOOXML.java:111,169`, `TestOOXMLVersions.java:86` and
`MassiveSignatureTest.java:229` are now excluded from **every lane that can actually run**, since
`RequiresGui` needs an xvfb lane that does not exist until Phase 1/5. OOXML signing has gone from
"never reached, because the reactor died upstream" to "deliberately never run" — no worse, but
no better, and now it *looks* handled.

*Recommendation.* File a production bug against `OOXMLOfficeObjectHelper` and reference it from
the four category comments. Once the headless guard lands, these four tests move back to the
default lane — that is the single largest coverage win available to Phase 1, and it is cheap.

**F3 — `integration-tests` is all-or-nothing and cannot run green anywhere.**

`pom.xml:647-652` empties `surefire.excluded.groups` entirely, so the profile selects
`RequiresWindows` + `RequiresMacOS` + `RequiresSmartCard` + `RequiresNss` + `RequiresGui` +
`RequiresNetwork` + `RequiresTriphaseServer` simultaneously. No machine satisfies that set, so
the profile is guaranteed red everywhere. Plan item 3 asked for a profile that "includes the ones
the environment supports". Demonstrated: running it on `afirma-core` selects
`TestDataDownloader.testDataDownloaderFile` (`@Category(RequiresWindows)`, reads
`file://c:/Windows/WindowsUpdate.log`) on Linux.

The good news is that the *mechanism* already supports precise lanes, because `excludedGroups`
reads a property — and I verified this works, which makes the fix documentation rather than code:

| Invocation on `afirma-core` | Result |
|---|---|
| default lane | 30 tests, 0 skipped; `TestHttpConnection` / `TestDataDownloader` network tests **not selected at all** |
| `-Dsurefire.excluded.groups=…RequiresWindows` | the 4 `RequiresNetwork` methods of `TestDataDownloader` **are** selected; `testDataDownloaderFile` (`RequiresWindows`) stays excluded |
| `-Dgroups=…RequiresNetwork -Dsurefire.excluded.groups=` | only the `RequiresNetwork` tests are selected |

A CLI `-D` overrides the POM `<properties>` value, so Phase 1 can build one CI job per category
without touching the poms.

*Recommendation.* Keep the profile as a convenience, but document the per-lane invocation, and in
Phase 1 give CI one lane per category rather than one `integration-tests` job.

**F4 — 11 surviving `@Ignore`s have no tracked issue.** Disclosed by the report; carried forward
as agreed (§2). List them in `TESTING.md` when Phase 1 writes it, so the debt is visible outside
the source.

### Worth fixing later

**F5 — The hardcoded `<argLine>` will silently disable JaCoCo in Phase 6.**
`pom.xml:181` sets `<argLine>-Djava.awt.headless=true -Dfile.encoding=UTF-8</argLine>` as a
literal. `jacoco:prepare-agent` works by *setting the `argLine` property*; an explicitly
configured `<argLine>` overrides it, and coverage silently comes back empty — the classic Maven
trap. Phase 6 wires JaCoCo into the `sonar` profile.
*Recommendation.* Change to `<argLine>@{argLine} -Djava.awt.headless=true -Dfile.encoding=UTF-8</argLine>`
now (harmless today, with `<argLine>` defaulted to empty via a property), or note it as a Phase 6
prerequisite. One-line fix; cheap to do now, annoying to debug later.

**F6 — `afirma-test-support` is deployable.** The plan (line 157) says the module should be
"built first in the reactor, **never deployed**". `afirma-test-support/pom.xml` is a plain
`jar` with no `<maven.deploy.skip>true</maven.deploy.skip>`, so `env-deploy` would publish it to
Maven Central alongside the real artifacts. Harmless to the app (it is a test-scope dependency and
ships in nothing), but it publishes an internal testing artifact under `es.gob.afirma`.
*Recommendation.* Add `<properties><maven.deploy.skip>true</maven.deploy.skip></properties>`.
Strictly this is a Phase 2 requirement, so it is early rather than late.

**F7 — `-P autofirma` alone now fails outright.** The `autofirma` profile (`pom.xml:300`) is the
only one of the six `<modules>` profiles without `afirma-test-support`, which is correct — it is a
fragment meant to be combined with `env-dev`. But it means `-P autofirma` alone now fails to
resolve `afirma-test-support` from Maven Central (the module has never been published), where
before it merely did the *wrong* thing quietly (resolving stale core artifacts). The plan and the
report both say never to use it alone.
*Verdict: an improvement, not a regression* — a misuse that used to fail silently now fails
loudly. Worth one line in `TESTING.md` so nobody files it as a bug.

### Nits

**F8 — `TestMacKeyChain` is categorised inconsistently.** `TestMacKeyChain.java:40` carries
`@Category(RequiresMacOS.class)`; its sibling `testSystemKeyChain` at `:86-88` has only a
commented-out `//@Ignore` and no category, so it runs in the default lane and passes vacuously via
`if (!Platform.OS.MACOSX.equals(Platform.getOS())) { return; }` (`:89-91`). It is a green test
that asserts nothing on Linux. Either give it `RequiresMacOS` for consistency, or leave it — but
it is the one place the sweep was uneven.

**F9 — `TestFirewall` / `TestWaitForConnection` are categorised `RequiresNetwork` but only bind
`127.0.0.1`.** `TestFirewall.java:40` and `TestWaitForConnection.java:36`. Both would run fine in
the default lane, so two freshly-improved tests never execute. Defensible — they use a fixed port
(6629) that could clash in CI, and "firewall" is arguably a network concern — but if the intent is
maximum default-lane coverage, these two are the cheapest tests to reclaim.

**F10 — `afirma-test-support` is now the first module in every profile,** so any profile-level
compiler misconfiguration surfaces there first. On `minhap` this changes the reported failure site
from `afirma-core` to `afirma-test-support` for an unrelated pre-existing reason (§7), which could
mislead someone into blaming the new module. Purely diagnostic; no action needed beyond awareness.

### Checked and clean — no finding

- **Category filtering is correct in both directions and at both granularities** (§F3 table):
  class-level exclusion (`TestHttpConnection`) and method-level exclusion
  (`TestDataDownloader`'s 4 network methods vs its 1 Windows method) both behave exactly as
  intended, and `-Dgroups` positive selection works.
- **No assertion weakened.** Across the whole diff: 0 `Assert.*` lines removed,
  23 `@Test` added, 1 removed (`MassiveSignatureTest`'s combined method, which became a private
  helper plus two new `@Test` methods). No file's `@Test` count decreased.
- **No misspelled or unwired category.** All 72 annotations resolve to one of the 7 markers; all
  7 markers are used; **0 unused marker imports** across the tree.
- **Good judgement on a trap.** `TestXAdES.java:585` carries a `//@Ignore // Necesita GUI` comment,
  but the test runs and passes headless — verified present in the default-lane surefire report.
  A mechanical sweep would have categorised it on the comment and silently lost coverage. It was
  correctly left alone. Same for the `Platform`-guarded macOS test in F8.
- **The sixth profile.** The brief named five `<modules>` profiles; there are six —
  `env-deploy` also exists, and it was wired correctly too.

---

## 7. The profile question, settled

This was flagged as the highest-risk item: `afirma-test-support` is a new module that 32 modules
depend on, and three profiles were never tested. I ran all three on both branches.

| Profile | `master` | `phase0` | Verdict |
|---|---|---|---|
| `env-dev` (default) | FAIL — 19+1 of 33 modules | **SUCCESS — 33/33** | Fixed |
| `env-dev,autofirma` | FAIL — 26+2 of 38 | **SUCCESS — 38/38** | Fixed |
| `env-install` (`-Denv=install`) | FAIL at module **6/12** (`afirma-ui-simpleafirma`: `Pdf2ImagesConverterTests.testPdfLoadUi`) | FAIL at module **10/12** (`afirma-server-triphase-signer`) | **Improved, not regressed** |
| `sonar` | FAIL after **19** modules (`afirma-crypto-xades`, the redsara 403) | FAIL after **40** modules (`afirma-server-triphase-signer`) | **Improved, not regressed** |
| `minhap` | FAIL at module **1** (`afirma-core`) | FAIL at module **1** (`afirma-test-support`) | Equivalent — pre-existing |

**No regression exists.** The two residual failures are both pre-existing JDK-21 incompatibilities
in code Phase 0 never touched, in modules the plan puts explicitly out of scope:

- `afirma-server-triphase-signer/pom.xml:19` sets `<java.version>1.7</java.version>` with
  `<fork>true</fork>` (`:110-116`). JDK 21 dropped `-source/-target 7`. Breaks `env-install`,
  `sonar` and `env-deploy` on any modern JDK, on both branches.
- The `minhap` profile passes
  `-Xbootclasspath:${java.home}/lib/jsse.jar:…/rt.jar:…/jce.jar` — files that have not existed
  since JDK 9. `minhap` is a JDK-8-only profile and fails at its first module on either branch.

And the module wiring itself is provably correct: `mvn -Denv=install clean validate` on phase0 is
**BUILD SUCCESS** across all 12 modules, with `afirma-test-support` resolving first.

---

## 8. Coverage regression check

The brief's sharpest question: the default lane's test count fell 340 → 298, so did anything that
*passed* on `master` quietly stop running?

I compared surefire XML at **method granularity**, like-for-like on the full lane
(`master` with `-fae` vs `phase0`), with all `target/` directories wiped first so no stale reports
could contaminate the result.

| | `master` (`-fae`) | `phase0` |
|---|---:|---:|
| Test methods that **actually executed and passed** | 247 | **297** |
| Skipped at runtime | 94 | 11 |
| Failed | 2 | **0** |
| Selected total | 343 | 308 |

**The headline test-count drop is an illusion, and the report undersells its own result.**
Executed-and-passing tests went **up by 50**. The 340 → 298 fall counts tests that Surefire
selected and then skipped at runtime; those are not coverage.

**Exactly one genuine regression, and 45 gains:**

| Direction | Count | Detail |
|---|---:|---|
| Passed on `master`, **not selected** on `phase0` | **1** | `TestKeyStoreWindowsCertACA.testPkcs12` — see F-COV below |
| Passed on `master`, now `@Ignore`d | **0** | — |
| Newly running **and passing** on `phase0` | **45** | listed below |

The 45 gains are not padding — they are the modules the broken reactor never reached:

- **`afirma-crypto-validation` (11 tests)** — `TestSignatureValidation` (CAdES/PAdES/XAdES
  validation), `TestPdfMods` including **PDF shadow-attack detection**, `Test_PAdES_Inc475804`.
  Security-relevant signature-validation logic that had never been exercised in a build.
- **`afirma-server-triphase-signer-core` (15)** — CAdES tri-phase sign, cosign, countersign.
- **`afirma-core-massive` (2)**, **`afirma-crypto-ooxml` (2)**, **`afirma-ui-simple-configurator` (2)**,
  **`afirma-simple` (3)**, plus the 12 converted `main()` drivers' new assertions
  (`TestPathShortener` 4, `TestShortName` 3, `TestBinParser` 3).

### F-COV — the one real coverage loss (medium severity)

`afirma-core-keystores/src/test/java/es/gob/afirma/test/keystores/TestKeyStoreWindowsCertACA.java:31`
applies `@Category(RequiresWindows.class)` at **class** level, over three methods:

| Method | Needs Windows? |
|---|---|
| `testStandaloneKeyChain` (`:38`) | Yes — CAPI via `AOKeyStore.WINDOWS`. Was `@Ignore`d on `master` |
| `testMSCapi` (`:77`) | Yes — CAPI. Was `@Ignore`d on `master` |
| `testPkcs12` (`:93`) | **No** — loads `/ACA PF Administrativo Activo.p12` from the classpath via `KeyStore.getInstance("PKCS12")` and asserts the key entry. Pure JCE, no Windows API. **Was not `@Ignore`d on `master`, ran and passed on Linux** |

The class name made class-level annotation look natural, but it swept up a passing
platform-independent test. This is precisely the "coverage loss dressed up as a cleanup" pattern —
small in size, but it is the one thing in the sweep that is actually wrong.

*Recommendation.* Move the category from the class to the two methods that need it:

```java
// TestKeyStoreWindowsCertACA.java — remove @Category from line 31, then:
@Test @Category(RequiresWindows.class) public void testStandaloneKeyChain()  // :38
@Test @Category(RequiresWindows.class) public void testMSCapi()              // :77
@Test                                  public void testPkcs12()              // :93 — back in the default lane
```

**Specifically cleared** — the three cases the brief asked me to scrutinise:

- **`TestMacKeyChain`** (commented-out `@Ignore`): no loss. The previously-`@Ignore`d method
  correctly became `RequiresMacOS`; the other method still runs in the default lane (it
  self-guards on `Platform.getOS()`). See F8 for the consistency nit.
- **The `afirma-core-massive` OOXML split**: no loss — `pruebaTodasLasCombinacionesDeFirmaProgramatica`
  and `pruebaCambioDeFormatoEnCaliente` both **gained** (the module never ran on `master`). Only
  the genuinely-headless-hostile OOXML row moved out. The split is minimal and well documented
  (`MassiveSignatureTest.java:134-143`).
- **`RequiresGui` tests that might run headless**: none lost. And `TestXAdES.java:585` — which
  carries a `// Necesita GUI` comment — was correctly *not* categorised and still runs.

---

## 9. What Phase 1 should inherit — or fix first

**Inherit as-is.** The category scheme, the Surefire pin, the marker module and the reactor wiring
are all sound and need no rework. Phase 1's CI workflow can be written directly against them.

**Fix first, in this order:**

1. **Normalise line endings and add `.gitattributes`** (F1) — before merge, while the branch is
   still the only thing touching these 77 files. Every day this waits makes the conflict worse.
2. **Restore `TestKeyStoreWindowsCertACA.testPkcs12` to the default lane** (§F-COV) — a
   three-line annotation move, and the only actual coverage regression in the change.
3. **File the OOXML headless bug** (F2) and reference it from the four `RequiresGui` comments.
   Fixing it is the single biggest coverage win available to Phase 1: four tests and the entire
   OOXML signing path return to the default lane for what looks like a small guarded change.
4. **Give CI one job per category, not one `integration-tests` job** (F3), using the
   `-Dsurefire.excluded.groups=…` mechanism verified in §F3. Document the invocations in `TESTING.md`.
5. **Prepare `argLine` for JaCoCo** (F5) — one line now, avoids a silent-empty-coverage debugging
   session in Phase 6.
6. **Make flakes visible rather than removing the retry yet** (§5) — fail or annotate the build on
   `Flakes:` / `<flakyFailure>`, then drop `rerunFailingTestsCount` once CI is stable.

**Track, don't block:** the 11 reasoned `@Ignore`s (F4), the deploy-skip on `afirma-test-support`
(F6), and the JDK 21 breakage in `afirma-server-triphase-signer` / `minhap` (§7) — the latter is
out of the plan's scope but will block any attempt to run the release build on a modern JDK, and
Phase 1's Temurin 8 job will not catch it because it only covers `env-dev`.

**A note on the JDK 8 job.** The report is right that Java 8 compatibility remains unproven, and
Phase 1's Temurin 8 job is the guard. Add to its risk list that `afirma-server-triphase-signer`
targets 1.7 — on JDK 8 that compiles with a deprecation warning, so the JDK 8 job will *pass* where
the JDK 21 job fails. The `env-install` lane needs its own modern-JDK job to catch §7.
