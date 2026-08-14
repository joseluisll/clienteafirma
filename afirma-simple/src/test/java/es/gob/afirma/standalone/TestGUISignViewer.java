package es.gob.afirma.standalone;

import java.io.File;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.test.support.RequiresGui;

/** Pruebas del visor gr&aacute;fico de firmas.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s */
public final class TestGUISignViewer {

	//private static final String TEST_FILE_OOXML = "/samples/2_signed.docx"; //$NON-NLS-1$
	private static final String TEST_FILE_ODF = "/samples/2_signed.odt"; //$NON-NLS-1$

	private static void testFile(final String filePath) throws Exception {
		Assert.assertNotNull(
			"No se encuentra el fichero de prueba " + filePath, //$NON-NLS-1$
			TestGUISignViewer.class.getResource(filePath)
		);
		final File file = new File(
			TestGUISignViewer.class.getResource(filePath).toURI()
		);
		SimpleAfirma.main(
			new String[] {
				CommandLineCommand.VERIFY.toString().toLowerCase(),
				"-i", //$NON-NLS-1$
				file.getAbsolutePath()
			}
		);
	}

	/** Abre el visor gr&aacute;fico de firmas sobre un ODF firmado.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresGui.class)
	@Ignore // Prueba manual: SimpleAfirma.main() invoca System.exit() y aborta la JVM de pruebas
	public void testOpenSignViewerOnOdf() throws Exception {
		testFile(TEST_FILE_ODF);
	}

}
