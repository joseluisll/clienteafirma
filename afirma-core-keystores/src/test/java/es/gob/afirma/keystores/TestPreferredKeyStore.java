package es.gob.afirma.keystores;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.test.support.RequiresSmartCard;
import es.gob.afirma.test.support.RequiresWindows;

/** Pruebas de precedencia de almacenes en un almacen agregado.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s */
public final class TestPreferredKeyStore {

	/** Prueba de precedencia de almacenes en un almacen agregado CAPI - CERES 100% Java.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category({ RequiresWindows.class, RequiresSmartCard.class })
	public void testPreferredKeyStoreInAggregatedManager() throws Exception {

		final AggregatedKeyStoreManager aksm = AOKeyStoreManagerFactory.getAOKeyStoreManager(
			AOKeyStore.WINDOWS,
			null, // Lib
			"CAPI-CERES", // Description //$NON-NLS-1$
			AOKeyStore.WINDOWS.getStorePasswordCallback(null),
			null // Parent
		);
		Assert.assertNotNull("No se ha podido abrir el almacen de Windows", aksm); //$NON-NLS-1$

		final AOKeyStoreManager ceresKsm = new AOKeyStoreManager();
		ceresKsm.init(AOKeyStore.CERES, null, AOKeyStore.CERES.getStorePasswordCallback(null), null, false);
		ceresKsm.setPreferred(true);
		Assert.assertTrue("El almacen CERES deberia haber quedado marcado como preferente", ceresKsm.isPreferred()); //$NON-NLS-1$

		aksm.addKeyStoreManager(ceresKsm);

		final String[] aliases = aksm.getAliases();
		Assert.assertNotNull("El almacen agregado no ha devuelto alias", aliases); //$NON-NLS-1$
		for (final String alias : aliases) {
			System.out.println(alias);
		}
	}

}
