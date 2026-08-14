package es.gob.afirma.core.misc;

import org.junit.Assert;
import org.junit.Test;

/** Pruebas de acortado de ruta.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s */
public final class TestPathShortener {

	private static final String LONG_PATH = "C:\\Documents and Settings\\All Users\\Application Data\\Apple Computer\\iTunes\\SC Info\\SC Info.txt"; //$NON-NLS-1$

	/** Una ruta que no supera el l&iacute;mite se devuelve sin cambios. */
	@SuppressWarnings("static-method")
	@Test
	public void testShortPathIsUnchanged() {
		Assert.assertEquals("C:\\temp", AOFileUtils.pathLengthShortener("C:\\temp", 20)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Una ruta larga se acorta sin superar el l&iacute;mite y conservando el nombre del fichero. */
	@SuppressWarnings("static-method")
	@Test
	public void testLongPathIsShortenedKeepingFileName() {
		final String shortened = AOFileUtils.pathLengthShortener(LONG_PATH, 60);
		Assert.assertNotNull(shortened);
		Assert.assertTrue(
			"La ruta acortada (" + shortened.length() + ") supera el limite", //$NON-NLS-1$ //$NON-NLS-2$
			shortened.length() <= 60
		);
		Assert.assertTrue(
			"La ruta acortada no conserva el nombre del fichero: " + shortened, //$NON-NLS-1$
			shortened.endsWith("SC Info.txt") //$NON-NLS-1$
		);
	}

	/** El acortado funciona con distintos separadores y esquemas. */
	@SuppressWarnings("static-method")
	@Test
	public void testShorteningOfVariousPathStyles() {
		final String[] paths = {
			"C:\\1\\2\\3\\4\\5\\test.txt", //$NON-NLS-1$
			"C:/1/2/3/4/5/test.txt", //$NON-NLS-1$
			"\\\\server\\p1\\p2\\p3\\p4\\p5\\p6", //$NON-NLS-1$
			"http://www.rgagnon.com/p1/p2/p3/p4/p5/pb.html" //$NON-NLS-1$
		};
		for (final String path : paths) {
			final String shortened = AOFileUtils.pathLengthShortener(path, 20);
			Assert.assertNotNull(shortened);
			Assert.assertTrue(
				"La ruta acortada de '" + path + "' supera el limite: " + shortened, //$NON-NLS-1$ //$NON-NLS-2$
				shortened.length() <= 20
			);
		}
	}

	/** Una ruta nula debe rechazarse. */
	@SuppressWarnings("static-method")
	@Test(expected = IllegalArgumentException.class)
	public void testNullPathIsRejected() {
		AOFileUtils.pathLengthShortener(null, 20);
	}

}
