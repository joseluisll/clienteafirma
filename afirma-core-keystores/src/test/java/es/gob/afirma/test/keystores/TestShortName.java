package es.gob.afirma.test.keystores;

import org.junit.Assert;
import org.junit.Test;

import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.keystores.KeyStoreUtilities;

/** Prueba de obtenci&oacute;n de nombre corto en Windows. */
public final class TestShortName {

	/** Fuera de Windows la ruta se devuelve sin cambios. */
	@SuppressWarnings("static-method")
	@Test
	public void testShortNameOutsideWindowsIsUnchanged() {
		if (Platform.OS.WINDOWS.equals(Platform.getOS())) {
			return;
		}
		final String[] paths = {
			"C:\\Program Files\\Nightly\\fnmt", //$NON-NLS-1$
			"C:", //$NON-NLS-1$
			"C:\\", //$NON-NLS-1$
			"lala", //$NON-NLS-1$
			"moricons.dll" //$NON-NLS-1$
		};
		for (final String path : paths) {
			Assert.assertEquals(path, KeyStoreUtilities.getWindowsShortName(path));
		}
	}

	/** Una ruta inexistente se devuelve sin cambios en cualquier plataforma. */
	@SuppressWarnings("static-method")
	@Test
	public void testShortNameOfMissingPathIsUnchanged() {
		final String path = "does-not-exist-" + System.nanoTime(); //$NON-NLS-1$
		Assert.assertEquals(path, KeyStoreUtilities.getWindowsShortName(path));
	}

	/** Una ruta nula se devuelve tal cual (nula). */
	@SuppressWarnings("static-method")
	@Test
	public void testShortNameOfNullIsNull() {
		Assert.assertNull(KeyStoreUtilities.getWindowsShortName(null));
	}

}
