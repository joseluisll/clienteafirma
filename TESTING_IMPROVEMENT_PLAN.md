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
| **`mvn test` never touches the application itself.** `afirma-simple` and the UI modules are only in the `autofirma`/`env-install` profiles, so the default build tests the crypto libraries but not AutoFirma. | root `pom.xml`, profiles `env-dev` vs `autofirma` |
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

*Goal: a reproducible, green `mvn test` for the default reactor **and** the `autofirma` profile.*

1. **Pin and configure Surefire** in root `pluginManagement`: explicit version, `-Djava.awt.headless=true`,
   UTF-8, `trimStackTrace=false`, and `rerunFailingTestsCount=1` (temporary, until flakes are gone).
2. **Declare `junit:junit` (test scope) explicitly in every module that has tests.** Today it
   arrives by accident through SpongyCastle; any dependency upgrade would silently break
   test compilation of 8 modules.
3. **Introduce test lanes with JUnit 4 `@Category`:** create marker interfaces in a new
   shared test module (see Phase 2): `RequiresNetwork`, `RequiresSmartCard`, `RequiresGui`,
   `RequiresWindows` / `RequiresMacOS` / `RequiresNss`, `RequiresTriphaseServer`.
   Surefire default run excludes all of them; a `verify`-bound failsafe/surefire execution or
   a `-Pintegration-tests` profile includes the ones the environment supports.
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

**Acceptance:** `mvn clean test` and `mvn clean test -P autofirma` both green on a
network-less Linux container; skip count in unit lane ≈ 0; every excluded test carries a category.

### Phase 1 — Continuous integration (GitHub Actions)

*Goal: every PR gets an automatic verdict.*

1. **Workflow `build.yml`:**
   * Job 1 (Linux, Temurin **8**): `mvn -B clean test` — guards the Java 8 source/target contract.
   * Job 2 (Linux, Temurin 11 and 17 matrix): same, catches the JDK-compat issues this
     codebase historically hits (e.g. the existing `TestJavaBug8182580`).
   * Job 3 (Linux): `mvn -B clean verify -P autofirma` — builds and tests the actual app modules.
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

*Goal: one source of truth for keystores, sample documents, and test doubles.*

1. **New module `afirma-test-support`** (built first in the reactor, never deployed;
   consumed as a normal `test`-scope dependency):
   * The `@Category` marker interfaces (Phase 0).
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

*Goal: cover the pure-logic core of the desktop app, ordered by risk × cheapness.*

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

*Goal: turn the ~44 skipped tri-phase/batch client tests and the TSA-dependent tests back on.*

1. **Tri-phase clients (`cadestri`/`xadestri`/`padestri`/`batch-client`):** point the
   existing tests at the Phase 2 local HTTP stub serving recorded pre-sign/post-sign
   responses. The protocol is plain HTTP + Base64/JSON/XML — no real server logic needed to
   test the client side. This alone converts 29+8+8+2 skipped tests into running ones.
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

*Goal: test AutoFirma the way browsers and the AGE integration kit actually drive it.*

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
   lane: launch `SimpleAfirma`, load a PDF, sign with the P12 fixture via `FakeAOUIManager`-free
   real dialogs, assert the result panel. Keep it to a handful of smoke paths — the
   protocol/CLI lanes above give better coverage per maintenance euro.

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

## 4. Sequencing and effort

| Phase | Depends on | Rough effort | Value |
|---|---|---|---|
| 0 — Harness stabilisation | — | 2–4 days | Unblocks everything; `mvn test` becomes trustworthy |
| 1 — CI | 0 | 1–2 days | Every PR gets a verdict; regressions visible |
| 2 — Test-support module | 0 | 3–5 days | Kills fixture duplication/rot; enables headless fakes |
| 3 — Unit tests for untested core | 2 | 1–2 weeks, parallelisable | Covers the security-sensitive input surface |
| 4 — De-flake / resurrect suites | 2 | 1 week | +80 real tests recovered from skip limbo |
| 5 — Headless integration lane | 2 (and small refactors) | 1–2 weeks | True end-to-end confidence, browser-flow parity |
| 6 — Gates & reporting | 1 | 1–2 days, then ongoing | Prevents backsliding |

Phases 0–1 are a single small PR series and should land first; 2–4 can proceed in parallel
per module afterwards; 5 is incremental behind the integration lane; 6 turns on as soon as
CI exists and tightens over time.

## 5. Risks and constraints

* **Java 8 source/target** caps tooling: Mockito ≤ 4.11, AssertJ 3.x line, JUnit 4 or
  Jupiter (both fine). All recommended tools above respect this.
* **JDK-version sensitivity** of crypto/XML code (see `TestJavaBug8182580`) is exactly why
  the CI matrix builds on 8, 11 and 17 rather than one JDK.
* **OS-coupled code** (`restoreconfig`, Firefox NSS, CAPI, Keychain) can only be honestly
  tested on its OS — hence category-filtered Windows/macOS CI jobs and a manual matrix,
  not pretend-coverage on Linux.
* **Static state in the app tier** means Phase 5 needs small refactors (`ExitHandler`,
  resettable launcher state). These are behaviour-preserving and should be reviewed as such,
  but they do touch production classes.
* Public-CA fixtures cannot be regenerated at will — replacing them with generated fixtures
  changes what some validation tests assert; each swap needs a per-test review of intent
  (expired-cert behaviour vs. valid-cert behaviour).
