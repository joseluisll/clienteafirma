package test.es.gob.afirma.signers.batch.server;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.signers.batch.client.BatchSigner;
import es.gob.afirma.test.support.RequiresTriphaseServer;

/** Pruebas del cliente de firma por lote.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class TestBatchSignerWithCounterSign {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final String CERT_PATH = "PFActivoFirSHA1.pfx"; //$NON-NLS-1$
	private static final String CERT_PASS = "12341234"; //$NON-NLS-1$
	private static final String CERT_ALIAS = "fisico activo prueba"; //$NON-NLS-1$

	private static final String BATCH_FILE = "/batch-with-countersign.xml"; //$NON-NLS-1$

	private static final String PRE_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPresigner"; //$NON-NLS-1$
	private static final String POST_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPostsigner"; //$NON-NLS-1$

	/** Prueba simple del cliente de firma por lote con contrafirma.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresTriphaseServer.class)
	public void testBatchSignWithCounterSign() throws Exception {

		final PrivateKeyEntry pke;
		final KeyStore ks = KeyStore.getInstance("PKCS12"); //$NON-NLS-1$
		try(
			final InputStream is = ClassLoader.getSystemResourceAsStream(CERT_PATH)
		) {
			ks.load(
				is,
				CERT_PASS.toCharArray()
			);
		}
		pke = (PrivateKeyEntry) ks.getEntry(CERT_ALIAS, new KeyStore.PasswordProtection(CERT_PASS.toCharArray()));

		final String res;
		try (
			final InputStream is = TestBatchSignerWithCounterSign.class.getResourceAsStream(BATCH_FILE)
		) {
			Assert.assertNotNull("No se encuentra el lote de prueba " + BATCH_FILE, is); //$NON-NLS-1$
			res = BatchSigner.signXML(
				AOUtil.getDataFromInputStream(is),
				PRE_URL,
				POST_URL,
				pke.getCertificateChain(),
				pke.getPrivateKey()
			);
		}

		Assert.assertNotNull("El servidor no ha devuelto resultado para el lote", res); //$NON-NLS-1$
		Assert.assertFalse("El servidor ha devuelto un resultado vacio para el lote", res.trim().isEmpty()); //$NON-NLS-1$
		LOGGER.info(res);
	}

}
