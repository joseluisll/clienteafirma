# OOXML signing fails with `HeadlessException` on a machine with no display

| | |
|---|---|
| **Status** | Open — documented, not fixed |
| **Component** | `afirma-crypto-ooxml` |
| **Severity** | High — OOXML signing is completely unavailable headlessly |
| **Affects** | `v1.9.1` (and earlier — the code is long-standing) |
| **Found by** | Phase 0 test-harness work; see `TESTING_PHASE0.md` and `TESTING_PHASE0_REVIEW.md` |
| **Production fix** | Not applied. Phase 0 is explicitly forbidden from touching `src/main`, and a change on the signing path needs its own review. |

---

## Summary

`AOOOXMLSigner.sign(...)` unconditionally reads the monitor count, screen resolution and colour
depth of the local display, and writes them into the Microsoft `SignatureInfoV1` block of every
OOXML signature. On a JVM with no display (`java.awt.headless=true`, or simply no `$DISPLAY` on
Linux) those calls throw `java.awt.HeadlessException`, and the whole signature operation fails.

**AutoFirma cannot sign a `.docx`/`.xlsx`/`.pptx` on a headless machine at all** — not from the
command line, not server-side.

## Where

`afirma-crypto-ooxml/src/main/java/es/gob/afirma/signers/ooxml/OOXMLOfficeObjectHelper.java`,
inside `getOfficeObject(...)` (declared at `:39`):

| Line | Code | Headless behaviour |
|---|---|---|
| `:119` | `GraphicsEnvironment.getLocalGraphicsEnvironment()` | **Safe** — returns a `HeadlessGraphicsEnvironment` |
| `:127` | `ge.getScreenDevices().length` → `<Monitors>` | **Throws `HeadlessException`** (the observed failure point) |
| `:132` | `Toolkit.getDefaultToolkit().getScreenSize()` → `<HorizontalResolutionElement>` / `<VerticalResolutionElement>` | **Throws `HeadlessException`** |
| `:154` | `ge.getScreenDevices()[0].getDisplayMode().getBitDepth()` → `<ColorDepth>` | **Throws `HeadlessException`** |

Note that `:119` is *not* the throw site, despite being the `GraphicsEnvironment` acquisition —
the JDK returns a headless environment object quite happily and only throws when a screen device
is actually requested. Three of the four sites throw.

## Call path

```
AOOOXMLSigner.sign(...)                       AOOOXMLSigner.java:314   (public AOSigner API)
  └─ AOOOXMLSigner.signOOXML(...)             AOOOXMLSigner.java:418
       └─ OOXMLXAdESSigner.getSignedXML(...)  OOXMLXAdESSigner.java:202
            └─ OOXMLOfficeObjectHelper.getOfficeObject(...)
                 └─ ge.getScreenDevices()     OOXMLOfficeObjectHelper.java:127   ← throws
```

`cosign(...)` (`AOOOXMLSigner.java:341`) reaches the same code and fails identically.

## Reproduction

Any headless JVM will do. Using the existing test suite on this repository:

```
mvn -B test -pl afirma-crypto-ooxml -am -P env-dev,integration-tests -Dtest=TestOOXML
```

(The Phase 0 Surefire configuration sets `-Djava.awt.headless=true`, so no display manipulation
is needed. `integration-tests` is required because the tests are categorised `RequiresGui` —
see "Relationship to the test categories" below.)

Observed on JDK 21.0.10 / Linux, 4 of 6 tests error:

```
es.gob.afirma.core.AOException: Error durante la firma OOXML: java.awt.HeadlessException
        (213005: Error desconocido en la generacion de la firma OOXML)
    at es.gob.afirma.signers.ooxml.AOOOXMLSigner.signOOXML(AOOOXMLSigner.java:425)
    at es.gob.afirma.signers.ooxml.AOOOXMLSigner.sign(AOOOXMLSigner.java:314)
Caused by: java.awt.HeadlessException
    at java.desktop/sun.java2d.HeadlessGraphicsEnvironment.getScreenDevices(HeadlessGraphicsEnvironment.java:52)
    at es.gob.afirma.signers.ooxml.OOXMLOfficeObjectHelper.getOfficeObject(OOXMLOfficeObjectHelper.java:127)
    at es.gob.afirma.signers.ooxml.OOXMLXAdESSigner.getSignedXML(OOXMLXAdESSigner.java:202)
    at es.gob.afirma.signers.ooxml.AOOOXMLSigner.signOOXML(AOOOXMLSigner.java:418)
```

The two tests that pass (`TestFormatDetection`, `testGetSignersStructure`) do not sign.

## Impact

1. **Command-line signing.** `CommandLineLauncher.java:920-921` accepts `-format ooxml` and
   instantiates `AOOOXMLSigner`. A scripted `sign -format ooxml` on a server or a CI box — the
   normal way to drive AutoFirma without a desktop — fails. (OOXML is no longer the auto-detected
   *default* format for Office documents; see the comment at `CommandLineLauncher.java:890`. It
   remains explicitly selectable, so the path is live.)
2. **Any server-side or batch embedding** of the `afirma-crypto-ooxml` module.
3. **CI test coverage.** Because the failure is environmental-looking, the four affected tests are
   currently categorised `RequiresGui` and therefore run in **no** automated lane. Until this is
   fixed, OOXML signing has zero regression protection.

Not affected: the desktop GUI application on a machine with a display, which is why this has gone
unnoticed.

## Why the current values are not worth preserving

`<Monitors>`, `<HorizontalResolutionElement>`, `<VerticalResolutionElement>` and `<ColorDepth>`
are descriptive metadata in Microsoft's `SignatureInfoV1` extension
(`http://schemas.microsoft.com/office/2006/digsig`). They record the environment the signature was
produced on. They are:

- **not cryptographically meaningful** — they are signed *as content*, but nothing validates them;
- **not required by Office** to consider a signature valid;
- already effectively arbitrary — the code hardcodes `OfficeVersion`/`ApplicationVersion` as
  `"16.0"` (`:110`, `:117`) and `SignatureProviderId` as an all-zero GUID (`:167`), so this block
  is already a set of plausible constants rather than a faithful description.

Defaulting them when headless is therefore consistent with what the surrounding code already does.

## Suggested fix

Guard the display queries with `GraphicsEnvironment.isHeadless()` and default the four values.
Sketch (not applied):

```java
// OOXMLOfficeObjectHelper.getOfficeObject(...), replacing lines 119-157

final int monitors;
final int screenWidth;
final int screenHeight;
final int colorDepth;

if (GraphicsEnvironment.isHeadless()) {
    // Sin entorno grafico no se puede consultar la pantalla. Estos valores son
    // metadatos descriptivos de SignatureInfoV1, no afectan a la validez de la
    // firma, asi que se usan valores por defecto para permitir la firma headless.
    monitors     = 1;
    screenWidth  = 1920;
    screenHeight = 1080;
    colorDepth   = 32;
}
else {
    final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    monitors     = ge.getScreenDevices().length;
    screenWidth  = screenSize.width;
    screenHeight = screenSize.height;
    colorDepth   = ge.getScreenDevices()[0].getDisplayMode().getBitDepth();
}
```

…then build the four elements from those locals.

Points for the reviewer of that change:

- `GraphicsEnvironment.isHeadless()` is the correct predicate (it honours both
  `java.awt.headless=true` and a genuinely absent display). It is Java-8-safe.
- A belt-and-braces `try/catch (HeadlessException)` around the display block would also cover
  exotic cases where `isHeadless()` is false but no screen device exists (some X11 forwarding and
  container setups). Consider catching as well as checking.
- `getScreenDevices()` can legitimately return an empty array, which would make `[0]` at `:154`
  throw `ArrayIndexOutOfBoundsException` even on a non-headless box. Worth fixing in the same pass.
- Changing these values alters signature *bytes* for OOXML. Any test that pins an expected digest
  of a whole OOXML signature will need regenerating — check before merging.

## Relationship to the test categories

Four tests are categorised `RequiresGui` **because of this defect**, not because they are really
GUI tests:

| Test | Location |
|---|---|
| `TestOOXML.testSignature` | `afirma-crypto-ooxml/src/test/java/es/gob/afirma/test/ooxml/TestOOXML.java:111` |
| `TestOOXML.testCoSignature` | `afirma-crypto-ooxml/src/test/java/es/gob/afirma/test/ooxml/TestOOXML.java:169` |
| `TestOOXMLVersions.testSignature` | `afirma-crypto-ooxml/src/test/java/es/gob/afirma/test/ooxml/TestOOXMLVersions.java:86` |
| `MassiveSignatureTest.pruebaCombinacionesDeFirmaProgramaticaOOXML` | `afirma-core-massive/src/test/java/es/gob/afirma/massive/MassiveSignatureTest.java:229` |

Each carries a comment pointing back to this document.

**When this defect is fixed, remove the `@Category(RequiresGui.class)` from all four** and let
them run in the default unit lane. That is the intended end state — the category is a marker for
a known bug, not a permanent statement about these tests.
