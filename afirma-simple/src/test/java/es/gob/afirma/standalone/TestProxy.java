package es.gob.afirma.standalone;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.core.misc.http.UrlHttpManagerFactory;
import es.gob.afirma.core.misc.http.UrlHttpMethod;
import es.gob.afirma.test.support.RequiresNetwork;

/** Pruebas de proxy.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class TestProxy {

	private static final String UNRESOLVABLE_URL = "https://demo.tgm"; //$NON-NLS-1$
	private static final String GENERAL_URL = "https://google.com"; //$NON-NLS-1$

	/** Comprueba que, aplicada la configuraci&oacute;n de proxy, se descarga una URL general.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresNetwork.class)
	public void testReadUrlThroughProxy() throws Exception {
		ProxyUtil.setProxySettings();
		final byte[] data = UrlHttpManagerFactory.getInstalledManager().readUrl(GENERAL_URL, UrlHttpMethod.GET);
		Assert.assertNotNull("No se han obtenido datos de " + GENERAL_URL, data); //$NON-NLS-1$
		Assert.assertTrue("Se han obtenido datos vacios de " + GENERAL_URL, data.length > 0); //$NON-NLS-1$
	}

	/** Comprueba que una URL no resoluble falla en lugar de devolver datos.
	 * @throws Exception En cualquier error distinto del esperado. */
	@SuppressWarnings("static-method")
	@Test(expected = IOException.class)
	@Category(RequiresNetwork.class)
	public void testReadUnresolvableUrlFails() throws Exception {
		ProxyUtil.setProxySettings();
		UrlHttpManagerFactory.getInstalledManager().readUrl(UNRESOLVABLE_URL, UrlHttpMethod.GET);
	}

}
