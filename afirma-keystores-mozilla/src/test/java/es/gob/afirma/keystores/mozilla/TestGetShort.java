package es.gob.afirma.keystores.mozilla;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.LoggerUtil;
import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.test.support.RequiresWindows;

/** Prueba de obtenci&oacute;n de nombre corto en Windows.
 * @author Tom&aacute;s Garc&iacute;-Mer&aacute;s Capote. */
public final class TestGetShort {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Obtiene el nombre corto (8+3) de un fichero o directorio indicado (con ruta).
	 * @param originalPath Ruta completa hacia el fichero o directorio que queremos pasar a nombre corto.
	 * @return Nombre corto del fichero o directorio con su ruta completa, o la cadena originalmente indicada si no puede
	 *         obtenerse la versi&oacute;n corta */
	private static String getShort(final String originalPath) {
		if (originalPath == null || !Platform.OS.WINDOWS.equals(Platform.getOS())) {
			return originalPath;
		}
		final File dir = new File(originalPath);
		if (!dir.exists()) {
			return originalPath;
		}
		final String[] command = new String[] { "cmd.exe", "/c", "for %f in (\"" + originalPath + "\") do @echo %~sf" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		try {
			final Process p = new ProcessBuilder(
				command
			).start();
			try (
				final InputStream is = p.getInputStream()
			) {
				return new String(AOUtil.getDataFromInputStream(is)).trim();
			}
		}
		catch(final Exception e) {
			LOGGER.warning("No se ha podido obtener el nombre corto de " + LoggerUtil.getCleanUserHomePath(originalPath) + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return originalPath;
	}

	/** Comprueba que se obtiene un nombre corto para el directorio temporal de Windows. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresWindows.class)
	public void testGetShortNameOfExistingDirectory() {
		final String path = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
		final String shortName = getShort(path);
		Assert.assertNotNull("El nombre corto no puede ser nulo", shortName); //$NON-NLS-1$
		Assert.assertFalse("El nombre corto no puede estar vacio", shortName.trim().isEmpty()); //$NON-NLS-1$
		LOGGER.info("Nombre largo: " + path + " / Nombre corto: " + shortName); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Una ruta inexistente se devuelve sin cambios. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresWindows.class)
	public void testGetShortNameOfMissingPathIsUnchanged() {
		final String path = "C:\\does-not-exist-" + System.nanoTime(); //$NON-NLS-1$
		Assert.assertEquals(path, getShort(path));
	}

}
