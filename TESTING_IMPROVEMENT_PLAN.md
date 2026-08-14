# AutoFirma Desktop Client — Testing Infrastructure Improvement Plan

**Scope:** the maintained modules that constitute the AutoFirma desktop application (the
`env-dev` reactor modules plus the `autofirma` profile modules: `afirma-simple`,
`afirma-ui-simple-configurator`, `afirma-ui-simple-configurator-common`,
`afirma-ui-core-jse`, `afirma-ui-core-jse-keystores`).

**Out of scope:** the modules listed under *"Módulos sin mantenimiento"* in `README.md`
(applet, miniapplet, webstart, standalone, windows-store, etc.), the AutoFirma plugins
(`afirma-simple-plugin-*`), and the server-side deployables (`afirma-server-triphase-signer`,
`afirma-signature-retriever`, `afirma-signature-storage`). The tri-phase *client* modules
(`afirma-crypto-*tri-client`, `afirma-crypto-batch-client`) are in scope because they ship
inside the desktop app.

---

## 1. Current state (measured on this repository, v1.9.1)

The findings below come from static inspection plus an actual `mvn test -fae` run of the
default reactor on JDK 21 / Maven 3.9.

### 1.1 Build & harness

| Finding | Evidence |
|---|---|
| **No CI at all** — no `.github/workflows`, no Jenkinsfile, nothing. Tests run only on developer machines. | repo root |
| **`maven-surefire-plugin` is completely unconfigured** — no version pin, no excludes, no skip flags anywhere. Behaviour depends on the Maven default (3.2.5 with Maven 3.9.x). | 0 hits for `surefire` in any `pom.xml` |
| **JUnit 4.13.2 is the only test library.** No Mockito/EasyMock, no Hamcrest imports, no AssertJ, no JUnit 5 anywhere. | root `pom.xml:61`, repo-wide grep |
| **JUnit reaches most crypto modules only transitively through `spongycastle:prov`** (its compile-scope `junit` dep, remapped to `test` by root `dependencyManagement`). `afirma-crypto-cades`, `-cades-multi`, `-xades`, `-pdf`, `-core-pkcs7`, `-validation`, `afirma-keystores-filters` and `afirma-ui-simple-configurator` declare no `junit` dependency of their own. | `mvn dependency:tree -pl afirma-crypto-cades` → `spongycastle:prov → junit:4.13.2:test` |
| **`mvn test` never touches the application itself.** `afirma-simple` and the UI modules are only in the `autofirma`/`env-install` profiles, so the default build tests the crypto libraries but not AutoFirma. Combining profiles works and yields the full 37-module reactor: `mvn -P env-dev,autofirma ...` (verified). Note that `-P autofirma` **alone** silently drops the core modules from the reactor and resolves them from Maven Central — i.e. it would test stale artifacts, not the working tree. | root `pom.xml` profiles; verified with `mvn -B -P env-dev,autofirma validate` |
| The build uses `source`/`target` 1.8, **not** `--release 8`, so compiling on a newer JDK can silently link against post-Java-8 APIs and only fail at runtime on 8. | root `pom.xml` compiler config |
| The `sonar` profile exists but has no coverage engine (no JaCoCo) wired to it. | root `pom.xml:315` |

### 1.2 Test suite health (19 desktop-relevant modules)

* **130 test classes / 328 `@Test` methods; 76 (23%) are `@Ignore`d**, most with reasons like
  `// Necesita un DNIe`, `// Necesita NSS`, `// Solo para Windows`, `// Necesita GUI`,
  `// Requiere de servidor remoto`.
* **15 test classes contain zero `@Test`** — they are `public static void main(...)` manual
  drivers (e.g. `afirma-simple/src/test/java/.../TestGUISignViewer.java`,
  `afirma-keystores-mozilla/.../TestBinParser.java`). Several don't even match Surefire's
  default `Test*`/`*Test` patterns, so they are dead weight.
* **Actual reactor run: ~330 tests execute, 2 modules fail, both for environmental reasons:**
  * `afirma-crypto-xades` → `TestXAdES.testSignExternallyDetached` downloads
    `http://estaticos.redsara.es/comunes/autofirma/autofirma.version` live (HTTP 403 behind a proxy).
  * `afirma-keystores-filters` → `TestRFC2254CertificateFilter` tries to open the local
    Firefox NSS store (`ClassNotFoundException: MozillaUnifiedKeyStoreManager`).
  * Their failure then skips downstream modules (`ooxml`, `validation`, `core-massive`,
    `triphase-signer-core`) in the reactor.
* **Whole modules are effectively disabled:** `afirma-crypto-xadestri-client` 29/29 skipped,
  `afirma-crypto-cadestri-client` 8/8, `afirma-crypto-padestri-client` 7/8,
  `afirma-crypto-batch-client` 2/2, `afirma-core-keystores` 14/16.
* Live tests still depend on **external services**: the Catalan TSA
  `http://psis.catcert.net/psis/catcert/tsp` (6 files across `-pdf`, `-xades`, `-cades`),
  `https://valide.redsara.es`, `localhost:8080` servlet deployments, and the unresolvable
  internal host `appprueba`.
* **Hardcoded developer paths** are baked into test sources (30 occurrences), e.g.
  `C:\Users\tomas\workspace_32\...cardos11.dll`, `C:\Users\carlos.gamuci\Desktop\pdf_visible.pdf`.
* **Fixture rot:** binary keystores are copy-pasted across modules
  (`ANF_PF_Activo.pfx` ×13 copies, `PFActivoFirSHA256.pfx` ×8, …) and several fixtures are
  expired or revoked real certificates (`...Caducado.pfx`, `Cert Valido hasta 2021...p12`,
  `00_colegiado-hsm_revoked.p12`) — a latent time-bomb for signature-validation tests.

### 1.3 Testability of the app tier (`afirma-simple` and friends)

* ~43% of `afirma-simple` classes (94/217) import Swing directly.
* `SimpleAfirma.main` and the `ProtocolInvocationLauncher*` family are static, hold mutable
  static state, and call `System.exit()` on error paths — untestable as-is.
* **But good seams already exist:**
  * `AOUIFactory.setUIManager(AOUIManager)` (`afirma-core/.../core/ui/AOUIFactory.java:98`)
    lets a headless fake auto-answer every dialog/password prompt.
  * `KeyStoreManager` interface + `Pkcs12KeyStoreManager` + `CachePasswordCallback` allow
    software-P12 keystores in place of smartcards/OS stores.
  * `PluginsManager` takes its plugins directory as a constructor argument.
  * `SignatureExecutor`, `SignatureResultViewer`, `Configurator`, `Console` are real
    interfaces implemented by the app and fakeable in tests.
* **The single richest untested target:** `afirma-core`'s
  `es.gob.afirma.core.misc.protocol` package — the entire `afirma://` URI parsing layer
  (15 files, ~3,260 lines, pure `String`/`byte[]` → POJO with declared exceptions) has
  **zero tests**, while being the app's security-sensitive external input surface.

---

## 2. Goals

1. `mvn test` (and CI) is **green, deterministic and meaningful** on a clean machine with no
   network, no smartcard, no GUI.
2. The **AutoFirma application modules are built and tested on every push/PR**, not just the
   crypto libraries.
3. Environment-dependent tests are **classified, not ignored**: they still exist, run in the
   right lane (integration/manual), and their requirements are machine-readable.
4. New tests target the **highest-risk untested logic** (protocol parsing, command line,
   batch JSON, preferences) before any GUI automation is attempted.
5. Coverage is **measured and ratcheted**, not guessed.

---

## 3. The plan

### Phase 0 — Stabilise the harness (prerequisite for everything else)

1. **Pin and configure Surefire** in root `pluginManagement`: explicit version, `-Djava.awt.headless=true`,
   UTF-8, `trimStackTrace=false`, and `rerunFailingTestsCount=1` (temporary, until flakes are gone).
2. **Declare `junit:junit` (test scope) explicitly in every module that has tests.** Today it
   arrives by accident through SpongyCastle; any dependency upgrade would silently break
   test compilation of 8 modules.
3. **Bootstrap a skeleton `afirma-test-support` module now** (it grows in Phase 2) holding
   only the JUnit 4 `@Category` marker interfaces: `RequiresNetwork`, `RequiresSmartCard`,
   `RequiresGui`, `RequiresWindows` / `RequiresMacOS` / `RequiresNss`,
   `RequiresTriphaseServer`. Surefire's default run excludes all of them via
   `<excludedGroups>`; a `-Pintegration-tests` profile includes the ones the environment
   supports. (Categories must live in a shared module on every test classpath — that is why
   the skeleton cannot wait for Phase 2.)
4. **Reclassify instead of `@Ignore`:** sweep the 76 `@Ignore`d methods and the 2 currently
   red tests into the categories above. The comment (`// Necesita un DNIe`) becomes the
   category. `@Ignore` remains only for genuinely broken/obsolete tests, each with a tracking issue.
5. **Quarantine the live-network calls in unit lanes now:** `TestXAdES.testSignExternallyDetached`
   (redsara.es) and `TestRFC2254CertificateFilter` (NSS) are the two that break the reactor today.
6. **Delete or convert the 15 `main()`-driver pseudo-tests.** Convert the salvageable ones
   (`TestBinParser`, `TestPathShortener`, `TestShortName`) into real asserting tests; delete
   the rest (they are launch scripts, not tests).
7. **Remove hardcoded `C:\Users\...` paths** — load fixtures from `src/test/resources` via
   classpath, temp dirs via `TemporaryFolder`/`java.nio.file.Files.createTempDirectory`.

**Acceptance:** `mvn clean test` and `mvn clean test -P env-dev,autofirma` both green on a
network-less Linux container; skip count in unit lane ≈ 0; every excluded test carries a category.

### Phase 1 — Continuous integration (GitHub Actions)

1. **Workflow `build.yml`:**
   * Job 1 (Linux, Temurin **8**): `mvn -B clean test` — guards the Java 8 source/target
     contract *and* catches post-Java-8 API leakage that `source`/`target` (without
     `--release`) lets through on newer JDKs.
   * Job 2 (Linux, Temurin 11 and 17 matrix): same, catches the JDK-compat issues this
     codebase historically hits (e.g. the existing `TestJavaBug8182580`).
   * Job 3 (Linux, Temurin 8): `mvn -B clean verify -P env-dev,autofirma` — builds and tests
     the actual app modules **together with** the core modules. (Never `-P autofirma` alone:
     that drops the core modules from the reactor and resolves them from Maven Central,
     i.e. it would test stale artifacts instead of the PR's code. Verified: the combined
     invocation yields the full 37-module reactor.)
   * Cache `~/.m2/repository`; always upload `**/target/surefire-reports` as artifacts.
2. **Integration lane (nightly or label-triggered):** runs `RequiresNetwork` +
   `RequiresTriphaseServer` categories, with the tri-phase server stub from Phase 4; add
   `xvfb-run` so `RequiresGui` tests can join later (Phase 5).
3. **Optional matrix extension:** `windows-latest` / `macos-latest` jobs running only
   `RequiresWindows` / `RequiresMacOS` categories (CAPI, Keychain) — cheap because the
   category filter keeps them small.
4. Add a build/test **status badge** to `README.md` and document the lanes in a new
   `TESTING.md` (how to run each lane, what each category needs).

**Acceptance:** red PRs are blocked; surefire reports downloadable per run; nightly
integration lane reports separately from the unit lane.

### Phase 2 — Shared test fixtures module

1. **Grow `afirma-test-support`** (bootstrapped in Phase 0; built first in the reactor,
   never deployed; consumed as a normal `test`-scope dependency):
   * **One canonical copy of each test keystore** and loader helpers
     (`TestKeyStores.getRsaP12()`, `.getEcdsaP12()`, `.getExpiredP12()`, …), replacing the
     13 scattered copies of `ANF_PF_Activo.pfx` and friends.
   * **`FakeAOUIManager`**: a headless `AOUIManager` implementation with scriptable answers
     (auto-confirm, canned passwords, canned file selections), installed via
     `AOUIFactory.setUIManager(...)` in a JUnit rule. This single fake unlocks testing of
     most of the sign pipeline without a display.
   * **In-memory `java.util.prefs.PreferencesFactory`** (`-Djava.util.prefs.PreferencesFactory=...`
     via Surefire config) so `PreferencesManager` tests stop mutating the developer's real
     OS preferences/registry.
   * A tiny **local HTTP stub** helper based on the JDK's `com.sun.net.httpserver` (no new
     dependency, Java 8-safe) for tri-phase/storage/retriever client tests.
2. **Regenerate certificate fixtures programmatically** with SpongyCastle (already a
   dependency): long-validity self-signed CA + leaf certs for RSA/ECDSA, plus deliberately
   expired/revoked ones *generated* (not harvested from production CAs). Keep a small script
   or test-support factory so fixtures can be re-minted when algorithms age out. Audit and
   replace the currently expired real-world fixtures where the test intent is "valid cert".
3. Adopt **Mockito 4.11.x** (last Java 8-compatible line) in `afirma-test-support`'s
   dependency management for the cases an interface fake can't cover cleanly.

**Acceptance:** no duplicated keystore binaries across modules; unit tests run without
touching real OS preference stores; a documented fixture-regeneration path exists.

### Phase 3 — Unit tests for high-value untested logic

Ordered by risk × cheapness:

| Priority | Target | Why |
|---|---|---|
| 1 | `afirma-core` `es.gob.afirma.core.misc.protocol.*` (`ProtocolInvocationUriParser`, the 6 `UrlParameters*` classes, `ProtocolVersion`) | The `afirma://` URL is the app's untrusted external input surface — browser-controlled — and has **0 tests** for 3,260 lines. Table-driven tests per operation (sign/cosign/countersign/save/signandsave/selectcert/load/batch), covering malformed input, default handling, version gates, and the XML overloads. Realistic corpus URIs already exist in `TestProtocolInvocation.java`. |
| 2 | `afirma-simple` `CommandLineParameters` / `CommandLineCommand` | 685 lines of pure argv parsing behind the documented CLI; regressions here break scripted signing for third parties. |
| 3 | `afirma-crypto-batch-client` `JSON*Parser`, `TriphaseDataParser` | Pure JSON→model; currently the module's only tests are 100% ignored. |
| 4 | `afirma-simple` `standalone.signdetails.*SignAnalyzer` + `standalone.crypto.CompleteSignInfo` | Pure `byte[]` → model, drives the signature-details viewer; fixtures (`.csig`/`.xsig`/signed PDFs) already exist in the repo. |
| 5 | `afirma-ui-simple-configurator-common` (`PreferencesManager` two-tier user/system resolution, `PreferencesPlistHandler`, `ConfigDataInfo`) | 15 classes, zero Swing, zero tests today; runs on the in-memory prefs factory from Phase 2. |
| 6 | `afirma-simple-plugins-manager` `PluginsManager` (temp dir + fixture JARs: install/uninstall/verify/permission checks) | Security-relevant (JAR verification) and constructor-injectable already. |
| 7 | `ExtraParamsHelper`, `DataAnalizerUtil`, `ProxyConfig`, `afirma-ui-utils` `ImageUtils`, `afirma-ui-core-jse-keystores` `PrincipalStructure`/`CertificateLine` label building | Small pure helpers; cheap wins that also pin behaviour before any refactoring. |

**Acceptance:** each target has a test class with meaningful branch coverage; protocol
parser reaches ≥80% line coverage; all new tests live in the unit lane (no category).

### Phase 4 — De-flake and resurrect the disabled suites

1. **Tri-phase clients (`cadestri`/`xadestri`/`padestri`/`batch-client`):** point the
   existing tests at the Phase 2 local HTTP stub serving recorded pre-sign/post-sign
   responses (the hardcoded `appprueba`/`localhost:8080` URLs become injectable test
   parameters). The protocol is plain HTTP + Base64/JSON/XML — no real server logic needed
   to test the client side. This alone converts 29+8+8+2 skipped tests into running ones.
2. **Timestamping tests (`psis.catcert.net` TSA):** stand up a minimal local RFC 3161
   responder with SpongyCastle in test-support, or record one response per digest algorithm;
   keep one live-TSA smoke test in the `RequiresNetwork` nightly lane.
3. **`estaticos.redsara.es` version check inside `TestXAdES`:** that assertion belongs to
   `Updater` tests, not XAdES signing — split it out, stub the HTTP call, keep a nightly
   network smoke test.
4. **Keystore tests:** everything that can run against `Pkcs12KeyStoreManager` should; real
   DNIe/PKCS#11/CAPI/Keychain tests stay categorized and become the documented **manual/OS
   lane** (with the Windows/macOS CI jobs from Phase 1 running the OS-store subset).
5. Re-audit remaining `@Ignore`s: each either gets a category + working environment, or a
   linked issue, or deletion.

**Acceptance:** unit-lane skip count for the tri-client modules drops from 44 to ~0; no test
in the default lane opens a network connection (verifiable by running offline).

### Phase 5 — Integration tests for the invocation surface (headless E2E)

1. **Headless protocol-to-signature tests:** with `FakeAOUIManager` + a P12 fixture
   (forced via the `keystore` extra param), drive `ProtocolInvocationLauncher.launch(...)`
   for `sign`/`cosign`/`countersign`/`save` URIs and assert on the produced signature bytes.
   Prerequisite refactor (small, behaviour-preserving):
   * route `forceCloseApplication`/`System.exit` through an injectable `ExitHandler`;
   * allow resetting the launcher's static state (`stickyKeyEntry`, protocol version) between tests.
2. **WebSocket service tests:** start `AfirmaWebSocketServer` on an ephemeral port, connect
   with a plain Java-WebSocket client, replay the JS integration kit's message flow
   (`echo`, fragmented messages, sign request), assert responses. Same for a basic
   `ServiceInvocationManager` socket round-trip (`tryPorts` gets its own unit test).
3. **CLI integration tests:** run `CommandLineLauncher` in-JVM against fixture files
   (sign → verify with the existing validation module) — this is the cheapest true
   end-to-end lane because it is genuinely headless already.
4. **Optional GUI smoke lane (last):** AssertJ-Swing 3.x under `xvfb-run` in the nightly
   lane: launch `SimpleAfirma`, load a PDF, sign with the P12 fixture, assert the result
   panel. Keep it to a handful of smoke paths — AssertJ-Swing is effectively unmaintained
   (last release 2020), so treat this lane as disposable; the protocol/CLI lanes above give
   better coverage per maintenance euro.

**Acceptance:** a green headless run that starts from an `afirma://sign?...` URI and ends
with a verified CAdES/XAdES/PAdES signature, on Linux CI, with no display and no network.

### Phase 6 — Quality gates and reporting

1. **JaCoCo** wired into the build (and into the existing `sonar` profile): record the
   baseline, then **ratchet** — fail PRs that lower per-module coverage; no big-bang targets.
2. Aggregate coverage + surefire reporting job in CI, published as build artifacts
   (or to the existing SonarQube if the project has one — the `sonar` profile suggests so).
3. **Fixture-expiry canary:** one unit test that walks the test keystores and fails 90 days
   before any certificate expires, so fixture rot is caught before it flakes.
4. Document everything in `TESTING.md`: lanes, categories, how to add a fixture, how to run
   the OS-specific and manual (DNIe) matrices.

### Deliberately deferred / not planned

* **JUnit 5 migration:** Jupiter runs on Java 8 and Surefire 3.x supports both engines, so a
  gradual `junit-vintage` migration is possible — but it's churn with little risk reduction.
  Reconsider after Phases 0–4; `@Category` maps cleanly to `@Tag` later.
* **Real smartcard (DNIe/CERES) automation:** stays a documented manual test matrix; no CI
  hardware assumption.
* Tests for the obsolete modules and the installer packaging (`afirma-simple-installer`) —
  out of scope per this plan's premise.

---

## 4. Per-phase assessment: goal, complexity, feasibility, risk

Complexity scale: **Low** = mechanical, few decisions, mostly config/sweeps ·
**Medium** = real design decisions or many coordinated small changes ·
**High** = touches production code, concurrency, or brittle external behaviour.

### Phase 0 — Harness stabilisation

* **Goal:** a reproducible, green, offline `mvn test` for the full 37-module reactor, with
  every environment-dependent test carrying a machine-readable category instead of a comment.
* **Complexity: Medium.** The Surefire/JUnit config itself is Low, but the sweep touches
  ~76 `@Ignore`d methods, 15 pseudo-tests and 8 poms across many modules; each
  reclassification needs a 1-minute judgement call (category vs. delete vs. keep-ignored).
  No production code is touched.
* **Feasibility: High — verified.** The suite already runs green on JDK 21 except for the
  two environmental failures identified; categories + `<excludedGroups>` is bog-standard
  JUnit 4/Surefire machinery; the profile combination needed for the full reactor is
  confirmed working (`mvn -B -P env-dev,autofirma validate` → 37 modules).
* **Risk: Low.** Worst case is misclassifying a test into the wrong lane (recoverable, visible
  in CI). One watch-item: deleting `main()` drivers may remove scripts some maintainer still
  uses manually — mitigate by converting rather than deleting where there is any doubt, and
  by keeping the deletions in a dedicated reviewable commit.

### Phase 1 — Continuous integration

* **Goal:** every push/PR gets an automatic build-and-test verdict covering both the core
  libraries and the actual AutoFirma application, on the JDKs that matter.
* **Complexity: Low.** Standard GitHub Actions Maven workflow; the only subtlety
  (profile combination) is already resolved and documented above.
* **Feasibility: High, with one unverified assumption.** Everything was validated locally on
  JDK 21 except the **Temurin 8 job**: the plugin stack (compiler 3.12.1, Surefire 3.2.5,
  Maven 3.9) is documented Java-8-compatible, but this repo has not been built on JDK 8 in
  this exercise. First CI run will tell; if a plugin balks, pinning older-but-compatible
  plugin versions for that job is a contained fix. Windows/macOS jobs are optional and can
  land later without blocking.
* **Risk: Low–Medium.** The JDK 8 job may surface genuine latent breakage (post-Java-8 API
  leakage enabled by `source`/`target` without `--release`) — that is the job doing its job,
  but it can delay a green pipeline; budget for fixing whatever it finds. Depends on Phase 0
  being merged first, otherwise CI is born red and gets ignored.

### Phase 2 — Shared test-support module

* **Goal:** one source of truth for keystores, fixtures and test doubles; unit tests that
  neither touch real OS preference stores nor depend on production CAs' certificate lifetimes.
* **Complexity: Medium.** New module + reactor wiring is easy; the real work is
  `FakeAOUIManager` (a 39-method interface, though most methods can throw
  `UnsupportedOperationException` until needed) and the programmatic certificate factory.
  The in-memory `PreferencesFactory` and HTTP stub are small, well-trodden patterns.
* **Feasibility: High.** All techniques are standard and Java-8-safe; SpongyCastle (already a
  dependency) fully supports certificate generation; the `AOUIFactory.setUIManager` seam
  exists today in production code — no refactoring needed to exploit it.
* **Risk: Medium.** Swapping harvested production certificates for generated ones changes
  what some validation tests actually assert — each swap needs a per-test review of intent
  (is the test about "a valid cert" or about *that* CA's chain?). Mitigate by migrating
  fixture-by-fixture with the existing tests as the safety net, and keeping the originals
  until their consumers are migrated. Deduplicating 260 fixture files is churny; do it
  module-by-module, not big-bang.

### Phase 3 — Unit tests for untested core logic

* **Goal:** meaningful coverage of the app's highest-risk pure logic — above all the
  `afirma://` protocol parser, the browser-controlled input surface (3,260 lines, 0 tests).
* **Complexity: Medium.** No infrastructure work left by this point (Phases 0/2 provide it);
  the effort is writing good table-driven cases and understanding intended semantics of
  under-documented parameters. Volume is the cost: seven target areas, parallelisable
  per-module by different contributors.
* **Feasibility: High — the targets were chosen for it.** All seven are pure logic verified
  to have no Swing/network/static-OS coupling (the `PreferencesManager` target needs only
  the Phase 2 in-memory backend). Realistic corpus inputs already exist in the repo.
* **Risk: Low–Medium.** Main hazard: tests may *reveal* real bugs or ambiguous behaviour in
  the parser (that is value, but each finding needs a fix-or-document decision, which can
  stall the "write tests" flow — triage findings into issues rather than blocking).
  Secondary hazard: writing characterisation tests that enshrine accidental behaviour;
  mitigate with review focus on "is this asserted because it's *intended*?".

### Phase 4 — De-flake and resurrect disabled suites

* **Goal:** the ~44 skipped tri-phase/batch client tests and the TSA-dependent tests run
  deterministically against local stubs; the default lane provably opens no network connection.
* **Complexity: Medium–High.** The HTTP-stubbing of tri-phase clients is Medium (record/replay
  of a simple HTTP+Base64 protocol, plus making hardcoded URLs injectable in test code).
  The local RFC 3161 responder is the High corner — non-trivial ASN.1 work even with
  SpongyCastle; the fallback (canned recorded TSA responses per digest algorithm) is much
  cheaper and acceptable.
* **Feasibility: Medium–High.** Client-side protocol is fully visible in this repo, so stubs
  can be authored from the code itself. Uncertainty: some ignored tests may have decayed
  beyond their environment dependency (bit-rotted expectations) — expect a tail of tests
  where resurrection is not worth it and deletion-with-issue is the honest outcome.
* **Risk: Medium.** Stub drift: a local stub can diverge from the real tri-phase server's
  behaviour and give false confidence — mitigate by keeping one live smoke test per protocol
  in the nightly `RequiresNetwork` lane as the canary. Time investment per resurrected test
  varies wildly; timebox per module and fall back to deletion-with-issue.

### Phase 5 — Headless integration tests of the invocation surface

* **Goal:** true end-to-end confidence for the flows browsers and scripts actually use:
  `afirma://` URI in → verified CAdES/XAdES/PAdES bytes out, headless, offline, on Linux CI.
* **Complexity: High.** This is the only phase that must modify production code
  (`ExitHandler` injection, resettable launcher statics) and deal with concurrency
  (socket/WebSocket servers, timeouts, ephemeral ports). Each individual change is small,
  but it is production-touching and needs careful review in a signature application.
* **Feasibility: Medium–High.** The enabling seams (`AOUIFactory.setUIManager`, P12 keystore
  forcing via extra params, `CommandLineLauncher` being genuinely headless) all exist and
  were verified in the codebase; the CLI lane (item 3) is essentially free and should land
  first as proof of concept. The WebSocket lane is feasible (plain Java-WebSocket client)
  but will need timeout discipline to avoid becoming the flakiest part of CI. The optional
  AssertJ-Swing lane is the least feasible piece (unmaintained tooling, last release 2020)
  — deliberately optional and disposable.
* **Risk: Medium–High.** (a) Behaviour-preserving refactors in the launcher carry regression
  risk in the app's most user-visible path — mitigate: land Phase 3/4 tests first so the
  refactor happens under coverage, keep diffs minimal and mechanical. (b) Server-based tests
  are the classic source of CI flakiness (port clashes, timing) — mitigate with ephemeral
  ports, generous deadlines, and quarantining flaky tests to nightly until proven stable.
  (c) Static state in `ProtocolInvocationLauncher` means test isolation bugs can produce
  order-dependent results — the resettable-state refactor is the fix, not an option.

### Phase 6 — Quality gates and reporting

* **Goal:** coverage measured and ratcheted per module; fixture expiry caught 90 days early;
  the whole testing setup documented in `TESTING.md`.
* **Complexity: Low.** JaCoCo + a ratchet check and a canary test are configuration and a
  small amount of glue.
* **Feasibility: High.** JaCoCo 0.8.x fully supports Java 8 bytecode and JDK 8–21 runtimes;
  the `sonar` profile already exists as a mount point. Only dependency: CI (Phase 1) must
  exist.
* **Risk: Low.** One policy hazard: a coverage *ratchet* set too aggressively punishes
  legitimate refactoring and teaches people to game the metric — ratchet on meaningful
  per-module thresholds with a small tolerance band, never a repo-wide vanity number.

### Summary

| Phase | Goal (one line) | Complexity | Feasibility | Risk | Effort | Depends on |
|---|---|---|---|---|---|---|
| 0 Harness | Green, offline, categorised `mvn test` for all 37 modules | Medium | High (verified) | Low | 2–4 days | — |
| 1 CI | Automatic verdict per PR, core + app, JDK 8/11/17 | Low | High (JDK 8 job unverified) | Low–Med | 1–2 days | 0 |
| 2 Fixtures | Single fixture source of truth + headless fakes | Medium | High | Medium | 3–5 days | 0 |
| 3 Unit tests | Cover protocol parser, CLI, batch JSON, prefs, plugins | Medium | High | Low–Med | 1–2 weeks (parallelisable) | 2 |
| 4 De-flake | 44 skipped tests resurrected against local stubs | Med–High | Med–High | Medium | 1 week | 2 |
| 5 Headless E2E | `afirma://` → verified signature, offline on CI | High | Med–High | Med–High | 1–2 weeks | 2 (+small refactors) |
| 6 Gates | Coverage ratchet, expiry canary, TESTING.md | Low | High | Low | 1–2 days | 1 |

Phases 0–1 are a single small PR series and should land first; 2–4 can proceed in parallel
per module afterwards; 5 is incremental behind the integration lane; 6 turns on as soon as
CI exists and tightens over time. The plan front-loads the low-risk/high-certainty work and
pushes everything that touches production code (Phase 5) behind the safety net the earlier
phases build.

## 5. Global constraints

* **Java 8 source/target** caps tooling: Mockito ≤ 4.11, AssertJ 3.x line, JUnit 4 or
  Jupiter (both fine). All recommended tools above respect this.
* **JDK-version sensitivity** of crypto/XML code (see `TestJavaBug8182580`) is exactly why
  the CI matrix builds on 8, 11 and 17 rather than one JDK.
* **OS-coupled code** (`restoreconfig`, Firefox NSS, CAPI, Keychain) can only be honestly
  tested on its OS — hence category-filtered Windows/macOS CI jobs and a manual matrix,
  not pretend-coverage on Linux.
* Public-CA fixtures cannot be regenerated at will — replacing them with generated fixtures
  changes what some validation tests assert; each swap needs a per-test review of intent
  (expired-cert behaviour vs. valid-cert behaviour).
